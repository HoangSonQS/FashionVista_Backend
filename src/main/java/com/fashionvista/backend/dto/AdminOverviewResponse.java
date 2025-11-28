package com.fashionvista.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminOverviewResponse {

    private BigDecimal dailyRevenue;
    private BigDecimal monthlyRevenue;
    private BigDecimal yearlyRevenue;
    private long pendingOrders;
    private long shippingOrders;
    private long completedOrders;
    private long cancelledOrders;
    private long lowStockProducts;
    private long newCustomers;
    private double conversionRate;
    private List<TopProductMetric> topProducts;

    @Data
    @Builder
    public static class TopProductMetric {
        private Long productId;
        private String productName;
        private long quantity;
        private BigDecimal revenue;
    }
}


