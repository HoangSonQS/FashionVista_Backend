package com.fashionvista.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fashionvista.backend.dto.OrderResponse;
import com.fashionvista.backend.dto.ShippingCreateRequest;
import com.fashionvista.backend.dto.ShippingWebhookPayload;
import com.fashionvista.backend.service.ShippingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminShippingControllerTest {

    @Mock
    private ShippingService shippingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminShippingController controller = new AdminShippingController(shippingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createShipping_ValidJsonBody_DeserializesAndCallsService() throws Exception {
        String body = "{\"carrier\":\"GHN\",\"serviceType\":\"EXPRESS\",\"weight\":500,\"note\":\"fragile\"}";
        when(shippingService.createShipping(eq("ORD-1"), any(ShippingCreateRequest.class)))
                .thenReturn(OrderResponse.builder().build());

        mockMvc.perform(post("/api/admin/shipping/ORD-1/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<ShippingCreateRequest> captor = ArgumentCaptor.forClass(ShippingCreateRequest.class);
        verify(shippingService).createShipping(eq("ORD-1"), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getCarrier()).isEqualTo("GHN");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getServiceType()).isEqualTo("EXPRESS");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getWeight()).isEqualTo(500.0);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getNote()).isEqualTo("fragile");
    }

    @Test
    void webhook_ValidJsonBody_DeserializesAndCallsService() throws Exception {
        String body = "{\"carrier\":\"GHN\",\"trackingNumber\":\"GHN-AAAA1111\",\"status\":\"delivered\",\"note\":\"ok\"}";

        mockMvc.perform(post("/api/admin/shipping/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<ShippingWebhookPayload> captor = ArgumentCaptor.forClass(ShippingWebhookPayload.class);
        verify(shippingService).handleWebhook(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getCarrier()).isEqualTo("GHN");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getTrackingNumber()).isEqualTo("GHN-AAAA1111");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getStatus()).isEqualTo("delivered");
    }
}
