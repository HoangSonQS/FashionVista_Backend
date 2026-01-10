package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminReportResponse;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final OrderRepository orderRepository;

    public AdminReportResponse getReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        // 1. Revenue Report
        AdminReportResponse.RevenueReport revenueReport = generateRevenueReport(start, end);

        // 2. Order Report
        AdminReportResponse.OrderReport orderReport = generateOrderReport(start, end);

        // 3. Top Customers
        List<AdminReportResponse.TopCustomerMetric> topCustomers = generateTopCustomers(start, end);

        return AdminReportResponse.builder()
                .revenueReport(revenueReport)
                .orderReport(orderReport)
                .topCustomers(topCustomers)
                .build();
    }

    private AdminReportResponse.RevenueReport generateRevenueReport(LocalDateTime start, LocalDateTime end) {
        List<AdminReportResponse.ChartDataPoint> dataPoints = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;

        Map<String, BigDecimal> dailyRevenueMap = new HashMap<>();

        // Fetch all orders in range to aggregate in memory (optimization: use DB
        // grouping query in future)
        // For now, simpler implementation for prototyping
        List<Order> orders = orderRepository.findByCreatedAtBetween(start, end);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");

        for (Order order : orders) {
            // Only count revenue from non-cancelled/refunded orders? Or count all paid?
            // Usually revenue is based on Completed or Paid. Let's use simplified logic:
            // Status != CANCELLED/REFUNDED
            if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.REFUNDED) {
                String dateKey = order.getCreatedAt().format(formatter);
                dailyRevenueMap.put(dateKey,
                        dailyRevenueMap.getOrDefault(dateKey, BigDecimal.ZERO).add(order.getTotal()));
                totalRevenue = totalRevenue.add(order.getTotal());
            }
        }

        // Fill missing days
        LocalDate current = start.toLocalDate();
        LocalDate endLoop = end.toLocalDate();

        while (current.isBefore(endLoop)) {
            String label = current.format(formatter);
            dataPoints.add(AdminReportResponse.ChartDataPoint.builder()
                    .label(label)
                    .value(dailyRevenueMap.getOrDefault(label, BigDecimal.ZERO))
                    .build());
            current = current.plusDays(1);
        }

        return AdminReportResponse.RevenueReport.builder()
                .totalRevenue(totalRevenue)
                .dataPoints(dataPoints)
                .build();
    }

    private AdminReportResponse.OrderReport generateOrderReport(LocalDateTime start, LocalDateTime end) {
        List<Order> orders = orderRepository.findByCreatedAtBetween(start, end);
        long totalOrders = orders.size();
        long cancelledOrders = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.REFUNDED)
                .count();

        double cancellationRate = totalOrders == 0 ? 0 : (double) cancelledOrders / totalOrders * 100;

        Map<OrderStatus, Long> distribution = orders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

        List<AdminReportResponse.OrderStatusDistribution> distList = distribution.entrySet().stream()
                .map(e -> AdminReportResponse.OrderStatusDistribution.builder()
                        .status(e.getKey().name())
                        .count(e.getValue())
                        .build())
                .collect(Collectors.toList());

        return AdminReportResponse.OrderReport.builder()
                .totalOrders(totalOrders)
                .cancelledOrders(cancelledOrders)
                .cancellationRate(Math.round(cancellationRate * 100.0) / 100.0)
                .statusDistribution(distList)
                .build();
    }

    private List<AdminReportResponse.TopCustomerMetric> generateTopCustomers(LocalDateTime start, LocalDateTime end) {
        // Warning: This logic is heavy if many orders. Better to use JPQL aggregation.
        // But for consistency with service layer logic, I'll use repository projection
        // if available.
        // Let's rely on OrderRepository Custom Query for performance.
        return orderRepository.findTopCustomers(start, end, PageRequest.of(0, 5)).stream()
                .map(proj -> AdminReportResponse.TopCustomerMetric.builder()
                        .userId(proj.getUserId())
                        .fullName(proj.getFullName())
                        .email(proj.getEmail())
                        .totalOrders(proj.getTotalOrders())
                        .totalSpent(proj.getTotalSpent())
                        .build())
                .collect(Collectors.toList());
    }
}
