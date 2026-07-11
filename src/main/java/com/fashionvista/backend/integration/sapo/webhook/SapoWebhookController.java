package com.fashionvista.backend.integration.sapo.webhook;

import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.integration.sapo.dto.SapoWebhookInventoryPayload;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/sapo")
@RequiredArgsConstructor
public class SapoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SapoWebhookController.class);

    private final SapoHmacVerifier hmacVerifier;
    private final ProductVariantRepository productVariantRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/inventory-update")
    @Transactional
    public ResponseEntity<Void> handleInventoryUpdate(
            HttpServletRequest request,
            @RequestHeader(value = "X-Sapo-Hmac-SHA256", required = false) String signature) throws IOException {

        byte[] rawBody = request.getInputStream().readAllBytes();

        if (!hmacVerifier.isValid(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SapoWebhookInventoryPayload payload = objectMapper.readValue(rawBody, SapoWebhookInventoryPayload.class);

        ProductVariant variant = resolveVariant(payload);
        if (variant == null) {
            log.warn("Sapo inventory webhook: no local variant found for variantId={} sku={}",
                    payload.getVariantId(), payload.getSku());
            return ResponseEntity.ok().build();
        }

        variant.setStock(payload.getInventoryQuantity());
        productVariantRepository.save(variant);
        return ResponseEntity.ok().build();
    }

    private ProductVariant resolveVariant(SapoWebhookInventoryPayload payload) {
        if (payload.getVariantId() != null) {
            Optional<ProductVariant> bySapoId = productVariantRepository
                    .findBySapoVariantId(String.valueOf(payload.getVariantId()));
            if (bySapoId.isPresent()) {
                return bySapoId.get();
            }
        }
        if (payload.getSku() != null) {
            return productVariantRepository.findBySku(payload.getSku()).orElse(null);
        }
        return null;
    }
}
