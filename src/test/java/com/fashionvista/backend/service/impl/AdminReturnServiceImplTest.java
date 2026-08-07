package com.fashionvista.backend.service.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.dto.UpdateReturnStatusRequest;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.ReturnItem;
import com.fashionvista.backend.entity.ReturnRequest;
import com.fashionvista.backend.entity.ReturnStatus;
import com.fashionvista.backend.integration.sapo.service.SapoInventorySyncService;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.repository.ReturnRequestRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminReturnServiceImplTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private SapoInventorySyncService sapoInventorySyncService;

    @InjectMocks
    private AdminReturnServiceImpl adminReturnService;

    @Test
    void updateStatus_ApprovedWithRestock_PushesStockToSapoForEachRestockedVariant() {
        ProductVariant variant = ProductVariant.builder().id(20L).stock(10).build();
        OrderItem orderItem = OrderItem.builder().id(1L).variant(variant).build();
        ReturnItem returnItem = ReturnItem.builder()
                .orderItem(orderItem)
                .status(ReturnStatus.APPROVED)
                .quantity(2)
                .acceptedQuantity(2)
                .build();
        Order order = Order.builder().id(100L).paymentMethod(PaymentMethod.COD).items(List.of(orderItem)).build();
        ReturnRequest returnRequest = ReturnRequest.builder()
                .id(1L)
                .order(order)
                .items(List.of(returnItem))
                .build();

        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(returnRequest));
        when(productVariantRepository.findById(20L)).thenReturn(Optional.of(variant));
        when(returnRequestRepository.save(returnRequest)).thenReturn(returnRequest);

        UpdateReturnStatusRequest request = new UpdateReturnStatusRequest();
        request.setStatus(ReturnStatus.APPROVED);
        request.setRestockItems(true);

        adminReturnService.updateStatus(1L, request);

        verify(sapoInventorySyncService, times(1)).pushStock(eq(20L));
    }
}
