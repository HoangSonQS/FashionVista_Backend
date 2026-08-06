package com.fashionvista.backend.integration.sapo.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fashionvista.backend.dto.UpdateOrderStatusRequest;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.service.AdminOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SapoWebhookControllerTest {

    @Mock
    private SapoHmacVerifier hmacVerifier;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AdminOrderService adminOrderService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SapoWebhookController controller = new SapoWebhookController(
                hmacVerifier, productVariantRepository, orderRepository, adminOrderService, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void handleInventoryUpdate_ValidSignature_UpdatesStock() throws Exception {
        String body = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":42}";
        when(hmacVerifier.isValid(any(byte[].class), eq("valid-signature"))).thenReturn(true);

        ProductVariant variant = ProductVariant.builder().id(1L).sku("SKU1").stock(0).build();
        when(productVariantRepository.findBySapoVariantId("123")).thenReturn(Optional.of(variant));

        mockMvc.perform(post("/webhook/sapo/inventory-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Sapo-Hmac-SHA256", "valid-signature")
                        .content(body))
                .andExpect(status().isOk());

        verify(productVariantRepository).save(variant);
        Assertions.assertEquals(42, variant.getStock());
    }

    @Test
    void handleInventoryUpdate_InvalidSignature_Returns401AndSkipsDb() throws Exception {
        String body = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":42}";
        when(hmacVerifier.isValid(any(byte[].class), eq("bad-signature"))).thenReturn(false);

        mockMvc.perform(post("/webhook/sapo/inventory-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Sapo-Hmac-SHA256", "bad-signature")
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(productVariantRepository, never()).findBySapoVariantId(anyString());
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void handleInventoryUpdate_VariantNotFound_Returns200AndSkipsUpdate() throws Exception {
        String body = "{\"variant_id\":999,\"sku\":\"UNKNOWN-SKU\",\"inventory_quantity\":10}";
        when(hmacVerifier.isValid(any(byte[].class), eq("valid-signature"))).thenReturn(true);
        when(productVariantRepository.findBySapoVariantId("999")).thenReturn(Optional.empty());
        when(productVariantRepository.findBySku("UNKNOWN-SKU")).thenReturn(Optional.empty());

        mockMvc.perform(post("/webhook/sapo/inventory-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Sapo-Hmac-SHA256", "valid-signature")
                        .content(body))
                .andExpect(status().isOk());

        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void handleOrderFulfilled_ValidSignature_UpdatesStatusToDelivered() throws Exception {
        String body = "{\"id\":\"555\",\"order_number\":\"FV-001\"}";
        when(hmacVerifier.isValid(any(byte[].class), eq("valid-signature"))).thenReturn(true);

        Order order = Order.builder().id(1L).build();
        when(orderRepository.findBySapoOrderId("555")).thenReturn(Optional.of(order));

        mockMvc.perform(post("/webhook/sapo/order-fulfilled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Sapo-Hmac-SHA256", "valid-signature")
                        .content(body))
                .andExpect(status().isOk());

        verify(adminOrderService).updateOrderStatus(eq(1L), any(UpdateOrderStatusRequest.class));
    }

    @Test
    void handleOrderFulfilled_InvalidSignature_Returns401AndSkipsUpdate() throws Exception {
        String body = "{\"id\":\"555\",\"order_number\":\"FV-001\"}";
        when(hmacVerifier.isValid(any(byte[].class), eq("bad-signature"))).thenReturn(false);

        mockMvc.perform(post("/webhook/sapo/order-fulfilled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Sapo-Hmac-SHA256", "bad-signature")
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(orderRepository, never()).findBySapoOrderId(anyString());
        verify(adminOrderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void handleOrderFulfilled_OrderNotFound_Returns200AndSkipsUpdate() throws Exception {
        String body = "{\"id\":\"999\",\"order_number\":\"FV-002\"}";
        when(hmacVerifier.isValid(any(byte[].class), eq("valid-signature"))).thenReturn(true);
        when(orderRepository.findBySapoOrderId("999")).thenReturn(Optional.empty());

        mockMvc.perform(post("/webhook/sapo/order-fulfilled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Sapo-Hmac-SHA256", "valid-signature")
                        .content(body))
                .andExpect(status().isOk());

        verify(adminOrderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void handleOrderCancelled_ValidSignature_UpdatesStatusToCancelled() throws Exception {
        String body = "{\"id\":\"777\",\"order_number\":\"FV-003\"}";
        when(hmacVerifier.isValid(any(byte[].class), eq("valid-signature"))).thenReturn(true);

        Order order = Order.builder().id(2L).build();
        when(orderRepository.findBySapoOrderId("777")).thenReturn(Optional.of(order));

        mockMvc.perform(post("/webhook/sapo/order-cancelled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Sapo-Hmac-SHA256", "valid-signature")
                        .content(body))
                .andExpect(status().isOk());

        verify(adminOrderService).updateOrderStatus(eq(2L),
                argThat(req -> req.getStatus() == OrderStatus.CANCELLED));
    }
}
