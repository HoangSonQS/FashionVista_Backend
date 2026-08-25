package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.entity.VoucherType;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoDiscountCodeResponse;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import com.fashionvista.backend.repository.VoucherRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoVoucherSyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private VoucherRepository voucherRepository;

    @InjectMocks
    private SapoVoucherSyncService sapoVoucherSyncService;

    private static SapoPriceRuleResponse priceRuleResponse(Long id) {
        SapoPriceRuleResponse.PriceRule priceRule = new SapoPriceRuleResponse.PriceRule();
        priceRule.setId(id);
        SapoPriceRuleResponse response = new SapoPriceRuleResponse();
        response.setPriceRule(priceRule);
        return response;
    }

    private static SapoDiscountCodeResponse discountCodeResponse(Long id) {
        SapoDiscountCodeResponse.DiscountCode discountCode = new SapoDiscountCodeResponse.DiscountCode();
        discountCode.setId(id);
        SapoDiscountCodeResponse response = new SapoDiscountCodeResponse();
        response.setDiscountCode(discountCode);
        return response;
    }

    @Test
    void pushVoucher_NeverSynced_CreatesPriceRuleAndDiscountCodeAndStoresBothIds() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.PENDING).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.createPriceRule(any(SapoPriceRuleRequest.class))).thenReturn(priceRuleResponse(501L));
        when(sapoApiClient.createDiscountCode(eq(501L), any(SapoDiscountCodeRequest.class)))
                .thenReturn(discountCodeResponse(701L));
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        sapoVoucherSyncService.pushVoucher(1L);

        assertThat(voucher.getSapoPriceRuleId()).isEqualTo(501L);
        assertThat(voucher.getSapoDiscountCodeId()).isEqualTo(701L);
        assertThat(voucher.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        verify(sapoApiClient, never()).updatePriceRule(any(), any());
    }

    @Test
    void pushVoucher_PriceRuleSucceedsDiscountCodeFails_MarksFailedAndKeepsPriceRuleId() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.PENDING).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.createPriceRule(any(SapoPriceRuleRequest.class))).thenReturn(priceRuleResponse(501L));
        when(sapoApiClient.createDiscountCode(eq(501L), any(SapoDiscountCodeRequest.class)))
                .thenThrow(new RuntimeException("Sapo down"));
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        sapoVoucherSyncService.pushVoucher(1L);

        assertThat(voucher.getSapoPriceRuleId()).isEqualTo(501L);
        assertThat(voucher.getSapoDiscountCodeId()).isNull();
        assertThat(voucher.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
    }

    @Test
    void pushVoucher_AlreadySynced_CallsUpdateNotCreate() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.SYNCED).sapoPriceRuleId(501L).sapoDiscountCodeId(701L).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.updatePriceRule(eq(501L), any(SapoPriceRuleRequest.class))).thenReturn(priceRuleResponse(501L));
        when(sapoApiClient.updateDiscountCode(eq(501L), eq(701L), any(SapoDiscountCodeRequest.class)))
                .thenReturn(discountCodeResponse(701L));
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        sapoVoucherSyncService.pushVoucher(1L);

        assertThat(voucher.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        verify(sapoApiClient, never()).createPriceRule(any());
        verify(sapoApiClient, never()).createDiscountCode(any(), any());
    }

    @Test
    void pushVoucher_FreeshipType_MapsToPercentageHundredWithShippingLineTarget() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("FREESHIP1").type(VoucherType.FREESHIP)
                .sapoSyncStatus(SapoSyncStatus.PENDING).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.createPriceRule(any(SapoPriceRuleRequest.class))).thenAnswer(invocation -> {
            SapoPriceRuleRequest request = invocation.getArgument(0);
            assertThat(request.getPriceRule().getValueType()).isEqualTo("percentage");
            assertThat(request.getPriceRule().getValue()).isEqualTo("100");
            assertThat(request.getPriceRule().getTargetType()).isEqualTo("shipping_line");
            return priceRuleResponse(502L);
        });
        when(sapoApiClient.createDiscountCode(eq(502L), any(SapoDiscountCodeRequest.class)))
                .thenReturn(discountCodeResponse(702L));
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        sapoVoucherSyncService.pushVoucher(1L);

        assertThat(voucher.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
    }

    @Test
    void pushVoucher_VoucherNotFound_DoesNothing() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.empty());

        sapoVoucherSyncService.pushVoucher(99L);

        verify(voucherRepository, never()).save(any());
    }

    @Test
    void deactivateVoucher_CallsUpdatePriceRuleWithEndsOnSet() {
        when(sapoApiClient.updatePriceRule(eq(501L), any(SapoPriceRuleRequest.class))).thenReturn(priceRuleResponse(501L));

        sapoVoucherSyncService.deactivateVoucher(501L);

        verify(sapoApiClient).updatePriceRule(eq(501L), any(SapoPriceRuleRequest.class));
    }

    @Test
    void deactivateVoucher_NullPriceRuleId_DoesNotCallSapo() {
        sapoVoucherSyncService.deactivateVoucher(null);

        verify(sapoApiClient, never()).updatePriceRule(any(), any());
    }

    @Test
    void deactivateVoucher_SapoThrows_DoesNotThrow() {
        when(sapoApiClient.updatePriceRule(eq(501L), any(SapoPriceRuleRequest.class)))
                .thenThrow(new RuntimeException("Sapo down"));

        sapoVoucherSyncService.deactivateVoucher(501L);

        verify(sapoApiClient).updatePriceRule(eq(501L), any(SapoPriceRuleRequest.class));
    }

    @Test
    void pullVoucher_Success_OverwritesLocalValueAndExpiresAt() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.SYNCED).sapoPriceRuleId(501L).build();
        SapoPriceRuleResponse response = priceRuleResponse(501L);
        response.getPriceRule().setValue("20");
        response.getPriceRule().setEndsOn("2026-09-01T00:00");
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.getPriceRule(501L)).thenReturn(response);
        when(voucherRepository.save(voucher)).thenReturn(voucher);

        boolean result = sapoVoucherSyncService.pullVoucher(1L);

        assertThat(result).isTrue();
        assertThat(voucher.getValue()).isEqualByComparingTo("20");
        assertThat(voucher.getExpiresAt().toString()).isEqualTo("2026-09-01T00:00");
    }

    @Test
    void pullVoucher_NeverPushed_ReturnsFalse() {
        Voucher voucher = Voucher.builder().id(1L).code("SUMMER10").sapoPriceRuleId(null).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        boolean result = sapoVoucherSyncService.pullVoucher(1L);

        assertThat(result).isFalse();
        verify(sapoApiClient, never()).getPriceRule(any());
    }

    @Test
    void pullVoucher_SapoThrows_ReturnsFalse() {
        Voucher voucher = Voucher.builder().id(1L).code("SUMMER10").sapoPriceRuleId(501L).build();
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(sapoApiClient.getPriceRule(501L)).thenThrow(new RuntimeException("Sapo down"));

        boolean result = sapoVoucherSyncService.pullVoucher(1L);

        assertThat(result).isFalse();
    }
}
