package com.fashionvista.backend.integration.sapo.synchealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.entity.VoucherType;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoPriceRuleResponse;
import com.fashionvista.backend.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoucherSyncHealthCheckTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private SapoApiClient sapoApiClient;

    @InjectMocks
    private VoucherSyncHealthCheck voucherSyncHealthCheck;

    private static SapoPriceRuleResponse responseWithValue(Long id, String value, String endsOn) {
        SapoPriceRuleResponse.PriceRule priceRule = new SapoPriceRuleResponse.PriceRule();
        priceRule.setId(id);
        priceRule.setValue(value);
        priceRule.setEndsOn(endsOn);
        SapoPriceRuleResponse response = new SapoPriceRuleResponse();
        response.setPriceRule(priceRule);
        return response;
    }

    @Test
    void checkAll_PendingWithinGracePeriod_ReturnsNoCandidates() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").sapoSyncStatus(SapoSyncStatus.PENDING)
                .createdAt(LocalDateTime.now().minusMinutes(2)).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of());

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_PendingPastGracePeriod_ReturnsNotSyncedCandidate() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").sapoSyncStatus(SapoSyncStatus.PENDING)
                .createdAt(LocalDateTime.now().minusMinutes(15)).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of());

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).entityId()).isEqualTo(1L);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.NOT_SYNCED);
    }

    @Test
    void checkAll_Failed_ReturnsSyncFailedCandidate() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").sapoSyncStatus(SapoSyncStatus.FAILED)
                .createdAt(LocalDateTime.now()).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of());

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.SYNC_FAILED);
    }

    @Test
    void checkAll_SyncedValueMismatch_ReturnsValueMismatchCandidate() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.SYNCED).sapoPriceRuleId(501L)
                .expiresAt(LocalDateTime.parse("2026-09-01T00:00")).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of());
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(sapoApiClient.getPriceRule(501L)).thenReturn(responseWithValue(501L, "20", "2026-09-01T00:00"));

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).discrepancyType()).isEqualTo(DiscrepancyType.VALUE_MISMATCH);
    }

    @Test
    void checkAll_SyncedValueMatches_ReturnsNoCandidates() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.SYNCED).sapoPriceRuleId(501L)
                .expiresAt(LocalDateTime.parse("2026-09-01T00:00")).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of());
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(sapoApiClient.getPriceRule(501L)).thenReturn(responseWithValue(501L, "10", "2026-09-01T00:00"));

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void checkAll_SapoApiThrows_ReturnsEmptyAndDoesNotThrow() {
        Voucher voucher = Voucher.builder()
                .id(1L).code("SUMMER10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .sapoSyncStatus(SapoSyncStatus.SYNCED).sapoPriceRuleId(501L).build();
        when(voucherRepository.findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus.SYNCED)).thenReturn(List.of());
        when(voucherRepository.findByActiveTrueAndSapoSyncStatus(SapoSyncStatus.SYNCED)).thenReturn(List.of(voucher));
        when(sapoApiClient.getPriceRule(501L)).thenThrow(new RuntimeException("Sapo down"));

        List<DiscrepancyCandidate> candidates = voucherSyncHealthCheck.checkAll();

        assertThat(candidates).isEmpty();
    }

    @Test
    void domain_ReturnsVoucher() {
        assertThat(voucherSyncHealthCheck.domain()).isEqualTo(com.fashionvista.backend.entity.SyncDomain.VOUCHER);
    }
}
