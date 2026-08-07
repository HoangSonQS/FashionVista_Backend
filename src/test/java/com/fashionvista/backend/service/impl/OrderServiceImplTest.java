package com.fashionvista.backend.service.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.service.SapoInventorySyncService;
import com.fashionvista.backend.repository.ProductVariantRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private SapoInventorySyncService sapoInventorySyncService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void restockItems_SaveSucceeds_PushesStockToSapo() {
        ProductVariant variant = ProductVariant.builder().id(10L).stock(5).build();
        OrderItem orderItem = OrderItem.builder().variant(variant).quantity(3).build();
        Order order = Order.builder().orderNumber("ORD-0001").items(List.of(orderItem)).build();

        when(productVariantRepository.findById(10L)).thenReturn(java.util.Optional.of(variant));

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(orderService, "restockItems", order);

        verify(sapoInventorySyncService, times(1)).pushStock(10L);
    }

    @Test
    void decreaseStock_ZeroAffected_ThrowsAndDoesNotPushStock() {
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-001").build();
        com.fashionvista.backend.entity.CartItem cartItem = com.fashionvista.backend.entity.CartItem.builder()
                .variant(variant)
                .quantity(3)
                .build();

        when(productVariantRepository.decreaseStockIfEnough(10L, 3)).thenReturn(0);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(orderService, "decreaseStock", cartItem));

        verify(sapoInventorySyncService, never()).pushStock(eq(10L));
    }

    @Test
    void decreaseStock_NonZeroAffected_PushesStockToSapo() {
        ProductVariant variant = ProductVariant.builder().id(12L).sku("SKU-002").build();
        com.fashionvista.backend.entity.CartItem cartItem = com.fashionvista.backend.entity.CartItem.builder()
                .variant(variant)
                .quantity(4)
                .build();

        when(productVariantRepository.decreaseStockIfEnough(12L, 4)).thenReturn(1);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(orderService, "decreaseStock", cartItem));

        verify(sapoInventorySyncService, times(1)).pushStock(12L);
    }

    @Test
    void decreaseStockForOrder_AffectedNonZero_PushesStockToSapo() {
        ProductVariant variant = ProductVariant.builder().id(11L).build();
        OrderItem orderItem = OrderItem.builder().variant(variant).quantity(2).build();
        Order order = Order.builder().orderNumber("ORD-0002").items(List.of(orderItem)).build();

        when(productVariantRepository.decreaseStockIfEnough(11L, 2)).thenReturn(1);

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(orderService, "decreaseStockForOrder", order);

        verify(sapoInventorySyncService, times(1)).pushStock(11L);
    }
}
