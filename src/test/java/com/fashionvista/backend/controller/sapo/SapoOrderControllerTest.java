package com.fashionvista.backend.controller.sapo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fashionvista.backend.config.SapoApiKeyFilter;
import com.fashionvista.backend.config.SapoProperties;
import com.fashionvista.backend.dto.sapo.SapoOrderItemRequest;
import com.fashionvista.backend.dto.sapo.SapoOrderRequest;
import com.fashionvista.backend.dto.sapo.SapoOrderStatusRequest;
import com.fashionvista.backend.entity.AccountStatus;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.exception.SapoExceptionHandler;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.VoucherService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SapoOrderControllerTest {

    private static final String VALID_KEY = "test-sapo-key";
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private MockMvc mockMvc;

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private VoucherService voucherService;

    @InjectMocks
    private SapoOrderController controller;

    private Order sampleOrder;
    private User sampleUser;
    private ProductVariant sampleVariant;

    @BeforeEach
    void setUp() {
        SapoProperties props = new SapoProperties();
        props.setApiKey(VALID_KEY);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addFilter(new SapoApiKeyFilter(props))
            .setControllerAdvice(new SapoExceptionHandler())
            .build();

        sampleUser = User.builder()
            .id(1L).email("a@example.com").fullName("Nguyen A")
            .phoneNumber("0901234567").role(UserRole.CUSTOMER)
            .status(AccountStatus.ACTIVE)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        Product product = Product.builder().id(1L).name("Áo thun").build();
        sampleVariant = ProductVariant.builder()
            .id(10L).sku("AT-S-WHITE").size("S").color("WHITE")
            .price(new BigDecimal("299000")).stock(20).product(product)
            .updatedAt(LocalDateTime.now()).build();

        sampleOrder = Order.builder()
            .id(1L).orderNumber("ORD-20260620-ABCD1234")
            .user(sampleUser).status(OrderStatus.PENDING)
            .paymentMethod(PaymentMethod.COD).paymentStatus(PaymentStatus.PENDING)
            .subtotal(new BigDecimal("299000")).shippingFee(BigDecimal.ZERO)
            .discount(BigDecimal.ZERO).total(new BigDecimal("299000"))
            .shippingAddress("{\"type\":\"POS\"}")
            .source("SAPO_POS").items(new ArrayList<>())
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    void getOrders_returnsPageResponse() throws Exception {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(sampleOrder)));

        mockMvc.perform(get("/api/sapo/v1/orders").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].orderNumber").value("ORD-20260620-ABCD1234"));
    }

    @Test
    void getOrder_found_returnsOrder() throws Exception {
        when(orderRepository.findByOrderNumber("ORD-20260620-ABCD1234"))
            .thenReturn(Optional.of(sampleOrder));

        mockMvc.perform(get("/api/sapo/v1/orders/ORD-20260620-ABCD1234")
                .header("X-Api-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getOrder_notFound_returns404() throws Exception {
        when(orderRepository.findByOrderNumber("NOTEXIST")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sapo/v1/orders/NOTEXIST").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isNotFound());
    }

    @Test
    void createOrder_validRequest_returns201() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(productVariantRepository.findBySku("AT-S-WHITE")).thenReturn(Optional.of(sampleVariant));
        when(productVariantRepository.decreaseStockIfEnough(anyLong(), anyInt())).thenReturn(1);
        when(orderRepository.save(any())).thenReturn(sampleOrder);

        SapoOrderItemRequest item = new SapoOrderItemRequest();
        item.setSku("AT-S-WHITE");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("299000"));

        SapoOrderRequest req = new SapoOrderRequest();
        req.setCustomerId(1L);
        req.setItems(List.of(item));
        req.setPaymentMethod("COD");

        mockMvc.perform(post("/api/sapo/v1/orders")
                .header("X-Api-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createOrder_insufficientStock_returns400() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(productVariantRepository.findBySku("AT-S-WHITE")).thenReturn(Optional.of(sampleVariant));
        when(productVariantRepository.decreaseStockIfEnough(anyLong(), anyInt())).thenReturn(0);

        SapoOrderItemRequest item = new SapoOrderItemRequest();
        item.setSku("AT-S-WHITE");
        item.setQuantity(100);
        item.setUnitPrice(new BigDecimal("299000"));

        SapoOrderRequest req = new SapoOrderRequest();
        req.setCustomerId(1L);
        req.setItems(List.of(item));
        req.setPaymentMethod("COD");

        mockMvc.perform(post("/api/sapo/v1/orders")
                .header("X-Api-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateOrderStatus_validStatus_returnsUpdated() throws Exception {
        when(orderRepository.findByOrderNumber("ORD-20260620-ABCD1234"))
            .thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any())).thenReturn(sampleOrder);

        SapoOrderStatusRequest req = new SapoOrderStatusRequest();
        req.setStatus("CONFIRMED");

        mockMvc.perform(put("/api/sapo/v1/orders/ORD-20260620-ABCD1234/status")
                .header("X-Api-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
    }
}
