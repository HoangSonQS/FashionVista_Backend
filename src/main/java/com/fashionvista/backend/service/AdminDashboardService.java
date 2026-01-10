package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AdminOverviewResponse;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.repository.OrderItemRepository;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.repository.projection.TopProductProjection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductImage;
import com.fashionvista.backend.repository.ProductRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public AdminOverviewResponse getOverview() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfYear = today.withDayOfYear(1).atStartOfDay();

        BigDecimal dailyRevenue = safe(orderRepository.sumTotalBetween(startOfDay, endOfDay));
        BigDecimal monthlyRevenue = safe(orderRepository.sumTotalBetween(startOfMonth, endOfDay));
        BigDecimal yearlyRevenue = safe(orderRepository.sumTotalBetween(startOfYear, endOfDay));

        long pendingOrders = orderRepository.countByStatus(OrderStatus.PENDING);
        long shippingOrders = orderRepository.countByStatus(OrderStatus.SHIPPING);
        long completedOrders = orderRepository.countByStatus(OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByStatusIn(List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED));

        long lowStockProducts = productVariantRepository.countByIsActiveTrueAndStockLessThanEqual(5);
        LocalDateTime sevenDaysAgo = startOfDay.minusDays(7);
        long newCustomers = userRepository.countByRoleAndCreatedAtBetween(UserRole.CUSTOMER, sevenDaysAgo, endOfDay);

        long totalOrders = orderRepository.count();
        double conversionRate = totalOrders == 0 ? 0d
                : BigDecimal.valueOf(completedOrders)
                        .divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();

        // Revenue Chart Data (Last 7 Days)
        List<AdminOverviewResponse.RevenueChartData> revenueChartData = new ArrayList<>();
        LocalDate chartDate = today.minusDays(6);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM");

        for (int i = 0; i < 7; i++) {
            LocalDateTime start = chartDate.atStartOfDay();
            LocalDateTime end = chartDate.plusDays(1).atStartOfDay();
            BigDecimal val = safe(orderRepository.sumTotalBetween(start, end));
            revenueChartData.add(AdminOverviewResponse.RevenueChartData.builder()
                    .date(chartDate.format(dateFormatter))
                    .value(val)
                    .build());
            chartDate = chartDate.plusDays(1);
        }

        // Recent Activities (Latest 10 Orders)
        List<AdminOverviewResponse.RecentActivity> recentActivities = orderRepository
                .findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(order -> AdminOverviewResponse.RecentActivity.builder()
                        .id(order.getOrderNumber())
                        .user(order.getUser() != null ? order.getUser().getFullName() : "Guest")
                        .action("placed order #" + order.getOrderNumber())
                        .time(getTimeAgo(order.getCreatedAt()))
                        .build())
                .collect(Collectors.toList());

        List<AdminOverviewResponse.TopProductMetric> topProducts = orderItemRepository
                .findTopProducts(PageRequest.of(0, 5))
                .stream()
                .map(this::mapTopProduct)
                .collect(Collectors.toList());

        return AdminOverviewResponse.builder()
                .dailyRevenue(dailyRevenue)
                .monthlyRevenue(monthlyRevenue)
                .yearlyRevenue(yearlyRevenue)
                .pendingOrders(pendingOrders)
                .shippingOrders(shippingOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .lowStockProducts(lowStockProducts)
                .newCustomers(newCustomers)
                .conversionRate(conversionRate)
                .topProducts(topProducts)
                .revenueChartData(revenueChartData)
                .recentActivities(recentActivities)
                .build();
    }

    private AdminOverviewResponse.TopProductMetric mapTopProduct(TopProductProjection projection) {
        String imageUrl = null;
        BigDecimal price = BigDecimal.ZERO;
        int stock = 0;

        if (projection.getProductId() != null) {
            Product product = productRepository.findById(projection.getProductId()).orElse(null);
            if (product != null) {
                if (!product.getImages().isEmpty()) {
                    imageUrl = product.getImages().get(0).getUrl();
                }
                price = product.getPrice();
                if (product.getVariants() != null) {
                    stock = product.getVariants().stream()
                            .mapToInt(v -> v.getStock() != null ? v.getStock() : 0)
                            .sum();
                }
            }
        }

        return AdminOverviewResponse.TopProductMetric.builder()
                .productId(projection.getProductId())
                .productName(projection.getProductName())
                .quantity(projection.getQuantity() != null ? projection.getQuantity() : 0L)
                .revenue(projection.getRevenue() != null ? projection.getRevenue() : BigDecimal.ZERO)
                .image(imageUrl)
                .price(price)
                .stock(stock)
                .build();
    }

    private String getTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null)
            return "";
        java.time.Duration duration = java.time.Duration.between(dateTime, LocalDateTime.now());
        long minutes = duration.toMinutes();
        if (minutes < 60)
            return minutes + " mins ago";
        long hours = duration.toHours();
        if (hours < 24)
            return hours + " hours ago";
        long days = duration.toDays();
        return days + " days ago";
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
