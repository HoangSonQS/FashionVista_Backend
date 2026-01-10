package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminReportResponse {

    private RevenueReport revenueReport;
    private OrderReport orderReport;
    private List<TopCustomerMetric> topCustomers;

    @Data
    @Builder
    public static class RevenueReport {
        private BigDecimal totalRevenue;
        private List<ChartDataPoint> dataPoints;
    }

    @Data
    @Builder
    public static class OrderReport {
        private long totalOrders;
        private long cancelledOrders;
        private double cancellationRate;
        private List<OrderStatusDistribution> statusDistribution;
    }

    @Data
    @Builder
    public static class OrderStatusDistribution {
        private String status;
        private long count;
    }

    @Data
    @Builder
    public static class ChartDataPoint {
        private String label; // Date
        private BigDecimal value;
    }

    @Data
    @Builder
    public static class TopCustomerMetric {
        private Long userId;
        private String fullName;
        private String email;
        private long totalOrders;
        private BigDecimal totalSpent;
    }
}
