package com.fashionvista.backend.integration.sapo.webhook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.integration.sapo.config.SapoOutboundProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoHmacVerifierTest {

    @Mock
    private SapoOutboundProperties properties;

    private SapoHmacVerifier verifier;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getWebhookSecret()).thenReturn("test-webhook-secret");
        verifier = new SapoHmacVerifier(properties);
    }

    @Test
    void isValid_CorrectSignature_ReturnsTrue() throws Exception {
        byte[] body = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":5}"
                .getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(body, "test-webhook-secret");

        assertTrue(verifier.isValid(body, signature));
    }

    @Test
    void isValid_TamperedBody_ReturnsFalse() throws Exception {
        byte[] originalBody = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":5}"
                .getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(originalBody, "test-webhook-secret");

        byte[] tamperedBody = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":9999}"
                .getBytes(StandardCharsets.UTF_8);

        assertFalse(verifier.isValid(tamperedBody, signature));
    }

    @Test
    void isValid_WrongSecret_ReturnsFalse() throws Exception {
        byte[] body = "{\"variant_id\":123,\"sku\":\"SKU1\",\"inventory_quantity\":5}"
                .getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(body, "a-different-secret");

        assertFalse(verifier.isValid(body, signature));
    }

    @Test
    void isValid_NullSignature_ReturnsFalse() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertFalse(verifier.isValid(body, null));
    }

    private String computeSignature(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}
