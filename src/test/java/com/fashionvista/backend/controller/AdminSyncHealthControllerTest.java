package com.fashionvista.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.dto.LinkSapoOrderRequest;
import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import com.fashionvista.backend.integration.sapo.service.SapoInventorySyncService;
import com.fashionvista.backend.integration.sapo.service.SapoOrderSyncService;
import com.fashionvista.backend.integration.sapo.synchealth.SyncDiscrepancyService;
import com.fashionvista.backend.integration.sapo.synchealth.SyncHealthScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class AdminSyncHealthControllerTest {

    private SyncDiscrepancyService syncDiscrepancyService;
    private SyncHealthScheduler syncHealthScheduler;
    private SapoInventorySyncService sapoInventorySyncService;
    private SapoOrderSyncService sapoOrderSyncService;
    private AdminSyncHealthController controller;

    @BeforeEach
    void setUp() {
        syncDiscrepancyService = mock(SyncDiscrepancyService.class);
        syncHealthScheduler = mock(SyncHealthScheduler.class);
        sapoInventorySyncService = mock(SapoInventorySyncService.class);
        sapoOrderSyncService = mock(SapoOrderSyncService.class);
        controller = new AdminSyncHealthController(
                syncDiscrepancyService, syncHealthScheduler, sapoInventorySyncService, sapoOrderSyncService);
    }

    @Test
    void pushToSapo_InventoryDomainSuccess_ResolvesDiscrepancy() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(1L).domain(SyncDomain.INVENTORY).entityId(10L).discrepancyType(DiscrepancyType.VALUE_MISMATCH).build();
        when(syncDiscrepancyService.findByIdOrThrow(1L)).thenReturn(discrepancy);
        when(sapoInventorySyncService.pushStock(10L)).thenReturn(true);

        ResponseEntity<Void> response = controller.pushToSapo(1L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(syncDiscrepancyService).resolve(discrepancy);
    }

    @Test
    void pushToSapo_InventoryDomainFailure_DoesNotResolveDiscrepancy() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(1L).domain(SyncDomain.INVENTORY).entityId(10L).discrepancyType(DiscrepancyType.VALUE_MISMATCH).build();
        when(syncDiscrepancyService.findByIdOrThrow(1L)).thenReturn(discrepancy);
        when(sapoInventorySyncService.pushStock(10L)).thenReturn(false);

        controller.pushToSapo(1L);

        verify(syncDiscrepancyService, never()).resolve(any(SyncDiscrepancy.class));
    }

    @Test
    void pushToSapo_OrderDomain_AlwaysResolvesAfterFireAndForgetPush() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(2L).domain(SyncDomain.ORDER).entityId(9L).discrepancyType(DiscrepancyType.SYNC_FAILED).build();
        when(syncDiscrepancyService.findByIdOrThrow(2L)).thenReturn(discrepancy);

        controller.pushToSapo(2L);

        verify(sapoOrderSyncService).pushOrder(9L);
        verify(syncDiscrepancyService).resolve(discrepancy);
    }

    @Test
    void pullFromSapo_OrderDomain_ThrowsIllegalArgumentException() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(2L).domain(SyncDomain.ORDER).entityId(9L).discrepancyType(DiscrepancyType.SYNC_FAILED).build();
        when(syncDiscrepancyService.findByIdOrThrow(2L)).thenReturn(discrepancy);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.pullFromSapo(2L));
    }

    @Test
    void linkSapoOrder_InventoryDomain_ThrowsIllegalArgumentException() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(1L).domain(SyncDomain.INVENTORY).entityId(10L).discrepancyType(DiscrepancyType.VALUE_MISMATCH).build();
        when(syncDiscrepancyService.findByIdOrThrow(1L)).thenReturn(discrepancy);
        LinkSapoOrderRequest request = new LinkSapoOrderRequest();
        request.setSapoOrderId("sapo-order-9");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.linkSapoOrder(1L, request));
    }

    @Test
    void linkSapoOrder_OrderDomain_LinksAndResolves() {
        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .id(2L).domain(SyncDomain.ORDER).entityId(9L).discrepancyType(DiscrepancyType.SYNC_FAILED).build();
        when(syncDiscrepancyService.findByIdOrThrow(2L)).thenReturn(discrepancy);
        LinkSapoOrderRequest request = new LinkSapoOrderRequest();
        request.setSapoOrderId("sapo-order-9");

        controller.linkSapoOrder(2L, request);

        verify(sapoOrderSyncService).linkSapoOrder(9L, "sapo-order-9");
        verify(syncDiscrepancyService).resolve(discrepancy);
    }

    @Test
    void runNow_DelegatesToScheduler() {
        controller.runNow();

        verify(syncHealthScheduler).runNow();
    }
}
