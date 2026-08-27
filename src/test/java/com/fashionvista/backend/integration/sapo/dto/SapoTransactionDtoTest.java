package com.fashionvista.backend.integration.sapo.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SapoTransactionDtoTest {

    @Test
    void request_SerializesToTransactionWrappedSnakeCaseJson() throws Exception {
        SapoTransactionRequest.Transaction transaction = SapoTransactionRequest.Transaction.builder()
                .amount(new BigDecimal("150000"))
                .kind("sale")
                .gateway("VNPay")
                .currency("VND")
                .status("success")
                .build();
        SapoTransactionRequest request = SapoTransactionRequest.builder().transaction(transaction).build();

        String json = new ObjectMapper().writeValueAsString(request);

        assertThat(json).contains("\"transaction\"");
        assertThat(json).contains("\"amount\":150000");
        assertThat(json).contains("\"kind\":\"sale\"");
        assertThat(json).contains("\"gateway\":\"VNPay\"");
        assertThat(json).contains("\"currency\":\"VND\"");
        assertThat(json).contains("\"status\":\"success\"");
    }

    @Test
    void response_DeserializesIdFromNestedTransactionObject() throws Exception {
        String json = "{\"transaction\":{\"id\":\"321\",\"order_id\":\"555\",\"unexpected_field\":true}}";

        SapoTransactionResponse response = new ObjectMapper().readValue(json, SapoTransactionResponse.class);

        assertThat(response.getTransaction().getId()).isEqualTo("321");
    }
}
