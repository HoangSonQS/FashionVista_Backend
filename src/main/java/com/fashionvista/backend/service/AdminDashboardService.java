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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

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
        double conversionRate = totalOrders == 0 ? 0d : BigDecimal.valueOf(completedOrders)
            .divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();

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
            .build();
    }

    private AdminOverviewResponse.TopProductMetric mapTopProduct(TopProductProjection projection) {
        return AdminOverviewResponse.TopProductMetric.builder()
            .productId(projection.getProductId())
            .productName(projection.getProductName())
            .quantity(projection.getQuantity() != null ? projection.getQuantity() : 0L)
            .revenue(projection.getRevenue() != null ? projection.getRevenue() : BigDecimal.ZERO)
            .build();
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}


