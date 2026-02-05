package com.fashionvista.backend.repository.projection;

import java.math.BigDecimal;

public interface TopCustomerProjection {
    Long getUserId();

    String getFullName();

    String getEmail();

    Long getTotalOrders();

    BigDecimal getTotalSpent();
}
