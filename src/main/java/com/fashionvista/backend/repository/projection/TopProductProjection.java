package com.fashionvista.backend.repository.projection;

import java.math.BigDecimal;

public interface TopProductProjection {

    Long getProductId();

    String getProductName();

    Long getQuantity();

    BigDecimal getRevenue();
}


