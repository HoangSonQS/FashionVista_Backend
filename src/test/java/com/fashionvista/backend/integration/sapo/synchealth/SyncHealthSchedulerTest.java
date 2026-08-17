package com.fashionvista.backend.integration.sapo.synchealth;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.service.EmailService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyncHealthSchedulerTest {

    @Test
    void runNow_OneCheckThrows_OtherChecksStillRunAndEmailSentForSurvivors() {
        SapoSyncHealthCheck failingCheck = mock(SapoSyncHealthCheck.class);
        when(failingCheck.domain()).thenReturn(SyncDomain.INVENTORY);
        when(failingCheck.checkAll()).thenThrow(new RuntimeException("Sapo unreachable"));

        SapoSyncHealthCheck workingCheck = mock(SapoSyncHealthCheck.class);
        when(workingCheck.domain()).thenReturn(SyncDomain.ORDER);
        DiscrepancyCandidate candidate = new DiscrepancyCandidate(5L, "ORD-0005", DiscrepancyType.NOT_SYNCED, "status=CONFIRMED");
        when(workingCheck.checkAll()).thenReturn(List.of(candidate));

        SyncDiscrepancyService syncDiscrepancyService = mock(SyncDiscrepancyService.class);
        SyncDiscrepancy newlyDetected = SyncDiscrepancy.builder().id(1L).domain(SyncDomain.ORDER).entityId(5L).build();
        when(syncDiscrepancyService.reconcile(SyncDomain.ORDER, List.of(candidate)))
                .thenReturn(List.of(newlyDetected));

        EmailService emailService = mock(EmailService.class);

        SyncHealthScheduler scheduler = new SyncHealthScheduler(
                List.of(failingCheck, workingCheck), syncDiscrepancyService, emailService);

        scheduler.runNow();

        verify(workingCheck, times(1)).checkAll();
        verify(failingCheck, times(1)).checkAll();
        verify(emailService, times(1)).sendSyncDiscrepancyAlert(List.of(newlyDetected));
        verify(syncDiscrepancyService, times(1)).markAlertSent(List.of(newlyDetected));
    }

    @Test
    void runNow_NoNewDiscrepancies_NoEmailSent() {
        SapoSyncHealthCheck check = mock(SapoSyncHealthCheck.class);
        when(check.domain()).thenReturn(SyncDomain.INVENTORY);
        when(check.checkAll()).thenReturn(List.of());

        SyncDiscrepancyService syncDiscrepancyService = mock(SyncDiscrepancyService.class);
        when(syncDiscrepancyService.reconcile(SyncDomain.INVENTORY, List.of())).thenReturn(List.of());

        EmailService emailService = mock(EmailService.class);

        SyncHealthScheduler scheduler = new SyncHealthScheduler(List.of(check), syncDiscrepancyService, emailService);

        scheduler.runNow();

        verify(emailService, never()).sendSyncDiscrepancyAlert(anyList());
    }
}
