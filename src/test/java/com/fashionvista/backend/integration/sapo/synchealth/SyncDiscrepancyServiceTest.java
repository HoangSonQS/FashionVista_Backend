package com.fashionvista.backend.integration.sapo.synchealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.repository.SyncDiscrepancyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncDiscrepancyServiceTest {

    @Mock
    private SyncDiscrepancyRepository syncDiscrepancyRepository;

    @InjectMocks
    private SyncDiscrepancyService syncDiscrepancyService;

    @Test
    void reconcile_NewCandidate_InsertsAndReturnsAsNewlyDetected() {
        DiscrepancyCandidate candidate = new DiscrepancyCandidate(1L, "SKU-001", DiscrepancyType.VALUE_MISMATCH, "DB=17, Sapo=20");
        when(syncDiscrepancyRepository.findByDomainAndEntityIdAndDiscrepancyTypeAndResolvedAtIsNull(
                SyncDomain.INVENTORY, 1L, DiscrepancyType.VALUE_MISMATCH))
                .thenReturn(Optional.empty());
        when(syncDiscrepancyRepository.save(any(SyncDiscrepancy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<SyncDiscrepancy> result = syncDiscrepancyService.reconcile(SyncDomain.INVENTORY, List.of(candidate));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEntityId()).isEqualTo(1L);
        assertThat(result.get(0).getResolvedAt()).isNull();
    }

    @Test
    void reconcile_ExistingOpenCandidate_UpdatesLastSeenAtAndDoesNotReturnAsNew() {
        DiscrepancyCandidate candidate = new DiscrepancyCandidate(1L, "SKU-001", DiscrepancyType.VALUE_MISMATCH, "DB=17, Sapo=20");
        SyncDiscrepancy existing = SyncDiscrepancy.builder()
                .id(99L)
                .domain(SyncDomain.INVENTORY)
                .entityId(1L)
                .entityLabel("SKU-001")
                .discrepancyType(DiscrepancyType.VALUE_MISMATCH)
                .build();
        when(syncDiscrepancyRepository.findByDomainAndEntityIdAndDiscrepancyTypeAndResolvedAtIsNull(
                SyncDomain.INVENTORY, 1L, DiscrepancyType.VALUE_MISMATCH))
                .thenReturn(Optional.of(existing));

        List<SyncDiscrepancy> result = syncDiscrepancyService.reconcile(SyncDomain.INVENTORY, List.of(candidate));

        assertThat(result).isEmpty();
        verify(syncDiscrepancyRepository, times(1)).save(existing);
    }

    @Test
    void resolve_SetsResolvedAtAndSaves() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder().id(1L).build();

        syncDiscrepancyService.resolve(discrepancy);

        assertThat(discrepancy.getResolvedAt()).isNotNull();
        verify(syncDiscrepancyRepository).save(discrepancy);
    }

    @Test
    void findByIdOrThrow_NotFound_ThrowsIllegalArgumentException() {
        when(syncDiscrepancyRepository.findById(404L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> syncDiscrepancyService.findByIdOrThrow(404L));
    }
}
