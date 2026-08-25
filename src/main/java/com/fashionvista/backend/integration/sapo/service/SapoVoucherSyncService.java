package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import com.fashionvista.backend.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SapoVoucherSyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoVoucherSyncService.class);
    private static final String VALUE_TYPE_PERCENTAGE = "percentage";
    private static final String VALUE_TYPE_FIXED_AMOUNT = "fixed_amount";
    private static final String TARGET_TYPE_SHIPPING_LINE = "shipping_line";

    private final SapoApiClient sapoApiClient;
    private final VoucherRepository voucherRepository;

    @Async("sapoVoucherTaskExecutor")
    @Transactional
    public void pushVoucher(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId).orElse(null);
        if (voucher == null) {
            log.warn("Sapo voucher sync: voucher id={} not found, skipping.", voucherId);
            return;
        }
        doPush(voucher);
        voucherRepository.save(voucher);
    }

    private void doPush(Voucher voucher) {
        try {
            SapoPriceRuleRequest priceRuleRequest = buildPriceRuleRequest(voucher);
            SapoPriceRuleResponse priceRuleResponse = voucher.getSapoPriceRuleId() == null
                    ? sapoApiClient.createPriceRule(priceRuleRequest)
                    : sapoApiClient.updatePriceRule(voucher.getSapoPriceRuleId(), priceRuleRequest);
            if (priceRuleResponse == null || priceRuleResponse.getPriceRule() == null
                    || priceRuleResponse.getPriceRule().getId() == null) {
                voucher.setSapoSyncStatus(SapoSyncStatus.FAILED);
                return;
            }
            voucher.setSapoPriceRuleId(priceRuleResponse.getPriceRule().getId());

            SapoDiscountCodeRequest discountCodeRequest = SapoDiscountCodeRequest.builder()
                    .discountCode(SapoDiscountCodeRequest.DiscountCode.builder().code(voucher.getCode()).build())
                    .build();
            SapoDiscountCodeResponse discountCodeResponse = voucher.getSapoDiscountCodeId() == null
                    ? sapoApiClient.createDiscountCode(voucher.getSapoPriceRuleId(), discountCodeRequest)
                    : sapoApiClient.updateDiscountCode(voucher.getSapoPriceRuleId(), voucher.getSapoDiscountCodeId(), discountCodeRequest);
            if (discountCodeResponse == null || discountCodeResponse.getDiscountCode() == null
                    || discountCodeResponse.getDiscountCode().getId() == null) {
                voucher.setSapoSyncStatus(SapoSyncStatus.FAILED);
                return;
            }
            voucher.setSapoDiscountCodeId(discountCodeResponse.getDiscountCode().getId());
            voucher.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        } catch (RuntimeException ex) {
            log.error("Sapo voucher sync failed for voucher id={}: {}", voucher.getId(), ex.getMessage(), ex);
            voucher.setSapoSyncStatus(SapoSyncStatus.FAILED);
        }
    }

    private SapoPriceRuleRequest buildPriceRuleRequest(Voucher voucher) {
        SapoPriceRuleRequest.PriceRule.PriceRuleBuilder priceRule = SapoPriceRuleRequest.PriceRule.builder()
                .title(voucher.getCode())
                .value(voucher.getValue() != null ? voucher.getValue().toPlainString() : null)
                .usageLimit(voucher.getUsageLimit())
                .startsOn(voucher.getStartsAt() != null ? voucher.getStartsAt().toString() : null)
                .endsOn(voucher.getExpiresAt() != null ? voucher.getExpiresAt().toString() : null);

        switch (voucher.getType()) {
            case PERCENT -> priceRule.valueType(VALUE_TYPE_PERCENTAGE);
            case FIXED_AMOUNT -> priceRule.valueType(VALUE_TYPE_FIXED_AMOUNT);
            case FREESHIP -> priceRule.valueType(VALUE_TYPE_PERCENTAGE).value("100").targetType(TARGET_TYPE_SHIPPING_LINE);
        }

        return SapoPriceRuleRequest.builder().priceRule(priceRule.build()).build();
    }

    @Async("sapoVoucherTaskExecutor")
    public void deactivateVoucher(Long sapoPriceRuleId) {
        if (sapoPriceRuleId == null) {
            return;
        }
        try {
            SapoPriceRuleRequest.PriceRule priceRule = SapoPriceRuleRequest.PriceRule.builder()
                    .endsOn(LocalDateTime.now().toString())
                    .build();
            sapoApiClient.updatePriceRule(sapoPriceRuleId, SapoPriceRuleRequest.builder().priceRule(priceRule).build());
        } catch (RuntimeException ex) {
            log.error("Sapo voucher deactivate failed for sapoPriceRuleId={}: {}", sapoPriceRuleId, ex.getMessage(), ex);
        }
    }

    @Transactional
    public boolean pullVoucher(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId).orElse(null);
        if (voucher == null || voucher.getSapoPriceRuleId() == null) {
            return false;
        }
        try {
            SapoPriceRuleResponse response = sapoApiClient.getPriceRule(voucher.getSapoPriceRuleId());
            if (response == null || response.getPriceRule() == null) {
                return false;
            }
            SapoPriceRuleResponse.PriceRule remote = response.getPriceRule();
            if (remote.getValue() != null) {
                voucher.setValue(new BigDecimal(remote.getValue()));
            }
            if (remote.getEndsOn() != null) {
                voucher.setExpiresAt(LocalDateTime.parse(remote.getEndsOn()));
            }
            voucherRepository.save(voucher);
            return true;
        } catch (RuntimeException ex) {
            log.error("Sapo voucher pull failed for voucher id={}: {}", voucherId, ex.getMessage(), ex);
            return false;
        }
    }
}
