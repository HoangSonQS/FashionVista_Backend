package com.fashionvista.backend.controller.sapo;

import static org.mockito.ArgumentMatchers.any;
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
import com.fashionvista.backend.dto.sapo.SapoVoucherRequest;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.entity.VoucherType;
import com.fashionvista.backend.exception.SapoExceptionHandler;
import com.fashionvista.backend.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
class SapoVoucherControllerTest {

    private static final String VALID_KEY = "test-sapo-key";
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private MockMvc mockMvc;
    @Mock private VoucherRepository voucherRepository;
    @InjectMocks private SapoVoucherController controller;

    private Voucher activeVoucher;

    @BeforeEach
    void setUp() {
        SapoProperties props = new SapoProperties();
        props.setApiKey(VALID_KEY);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addFilter(new SapoApiKeyFilter(props))
            .setControllerAdvice(new SapoExceptionHandler())
            .build();

        activeVoucher = Voucher.builder()
            .id(1L).code("SALE10").type(VoucherType.PERCENT)
            .value(new BigDecimal("10")).freeShipping(false)
            .minOrderTotal(new BigDecimal("500000"))
            .usageLimit(100).usedCount(5).active(true)
            .startsAt(LocalDateTime.now().minusDays(1))
            .expiresAt(LocalDateTime.now().plusDays(30))
            .build();
    }

    @Test
    void getVouchers_returnsPageResponse() throws Exception {
        when(voucherRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(activeVoucher)));

        mockMvc.perform(get("/api/sapo/v1/vouchers").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("SALE10"))
            .andExpect(jsonPath("$.data[0].available").value(true));
    }

    @Test
    void getVoucher_byCode_returnsVoucher() throws Exception {
        when(voucherRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(activeVoucher));

        mockMvc.perform(get("/api/sapo/v1/vouchers/SALE10").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.type").value("PERCENT"));
    }

    @Test
    void getVoucher_notFound_returns404() throws Exception {
        when(voucherRepository.findByCodeIgnoreCase("NOTEXIST")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sapo/v1/vouchers/NOTEXIST").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isNotFound());
    }

    @Test
    void createVoucher_newCode_returns201() throws Exception {
        when(voucherRepository.findByCodeIgnoreCase("NEW10")).thenReturn(Optional.empty());
        when(voucherRepository.save(any())).thenReturn(activeVoucher);

        SapoVoucherRequest req = new SapoVoucherRequest();
        req.setCode("NEW10");
        req.setType("PERCENT");
        req.setValue(new BigDecimal("10"));

        mockMvc.perform(post("/api/sapo/v1/vouchers")
                .header("X-Api-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());
    }

    @Test
    void createVoucher_duplicateCode_returns409() throws Exception {
        when(voucherRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(activeVoucher));

        SapoVoucherRequest req = new SapoVoucherRequest();
        req.setCode("SALE10");
        req.setType("PERCENT");

        mockMvc.perform(post("/api/sapo/v1/vouchers")
                .header("X-Api-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict());
    }

    @Test
    void useVoucher_validVoucher_incrementsUsedCount() throws Exception {
        when(voucherRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(activeVoucher));
        when(voucherRepository.save(any())).thenReturn(activeVoucher);

        mockMvc.perform(put("/api/sapo/v1/vouchers/SALE10/use")
                .header("X-Api-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referenceId\":\"SAPO-001\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
