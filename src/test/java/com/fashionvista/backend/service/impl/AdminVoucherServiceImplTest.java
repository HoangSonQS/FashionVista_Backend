package com.fashionvista.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.dto.AdminVoucherResponse;
import com.fashionvista.backend.dto.VoucherCreateRequest;
import com.fashionvista.backend.dto.VoucherUpdateRequest;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.entity.VoucherType;
import com.fashionvista.backend.integration.sapo.service.SapoVoucherSyncService;
import com.fashionvista.backend.repository.VoucherRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AdminVoucherServiceImplTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private SapoVoucherSyncService sapoVoucherSyncService;

    @InjectMocks
    private AdminVoucherServiceImpl adminVoucherService;

    private Voucher voucher;

    @BeforeEach
    void setUp() {
        voucher = Voucher.builder()
                .id(1L).code("SALE10").type(VoucherType.PERCENT).value(BigDecimal.TEN)
                .freeShipping(false).usedCount(0).active(true).build();
    }

    @Test
    void getAllVouchers_ReturnsPageOfVouchers() {
        Pageable pageable = PageRequest.of(0, 10);
        when(voucherRepository.findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(voucher)));

        Page<AdminVoucherResponse> result = adminVoucherService.getAllVouchers(null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("SALE10", result.getContent().get(0).getCode());
    }

    @Test
    void getVoucherById_Exists_ReturnsResponse() {
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        AdminVoucherResponse result = adminVoucherService.getVoucherById(1L);

        assertEquals("SALE10", result.getCode());
    }

    @Test
    void getVoucherById_NotFound_ThrowsEntityNotFoundException() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> adminVoucherService.getVoucherById(99L));
    }

    @Test
    void createVoucher_ValidRequest_SavesAndTriggersSapoPush() {
        VoucherCreateRequest request = new VoucherCreateRequest(
                "SALE20", VoucherType.PERCENT, BigDecimal.valueOf(20), false, null, null, true, null, null);
        when(voucherRepository.findByCodeIgnoreCase("SALE20")).thenReturn(Optional.empty());
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> {
            Voucher saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        AdminVoucherResponse result = adminVoucherService.createVoucher(request);

        assertNotNull(result);
        assertEquals("SALE20", result.getCode());
        verify(sapoVoucherSyncService).pushVoucher(2L);
    }

    @Test
    void createVoucher_DuplicateCode_ThrowsIllegalArgumentExceptionAndDoesNotPush() {
        VoucherCreateRequest request = new VoucherCreateRequest(
                "SALE10", VoucherType.PERCENT, BigDecimal.TEN, false, null, null, true, null, null);
        when(voucherRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(voucher));

        assertThrows(IllegalArgumentException.class, () -> adminVoucherService.createVoucher(request));
        verify(sapoVoucherSyncService, never()).pushVoucher(any());
    }

    @Test
    void updateVoucher_ValidRequest_SavesAndTriggersSapoPush() {
        VoucherUpdateRequest request = new VoucherUpdateRequest(
                null, null, BigDecimal.valueOf(15), null, null, null, null, null, null);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminVoucherResponse result = adminVoucherService.updateVoucher(1L, request);

        assertEquals(BigDecimal.valueOf(15), result.getValue());
        verify(sapoVoucherSyncService).pushVoucher(1L);
    }

    @Test
    void deleteVoucher_WithSapoPriceRuleId_DeletesAndTriggersDeactivate() {
        voucher.setSapoPriceRuleId(501L);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        adminVoucherService.deleteVoucher(1L);

        verify(voucherRepository).delete(voucher);
        verify(sapoVoucherSyncService).deactivateVoucher(501L);
    }

    @Test
    void deleteVoucher_WithoutSapoPriceRuleId_DeletesAndDoesNotTriggerDeactivate() {
        voucher.setSapoPriceRuleId(null);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        adminVoucherService.deleteVoucher(1L);

        verify(voucherRepository).delete(voucher);
        verify(sapoVoucherSyncService, never()).deactivateVoucher(any());
    }

    @Test
    void deleteVoucher_NotFound_ThrowsEntityNotFoundException() {
        when(voucherRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> adminVoucherService.deleteVoucher(99L));
        verify(sapoVoucherSyncService, never()).deactivateVoucher(any());
    }

    @Test
    void createVoucher_WithinActiveTransaction_DefersPushUntilAfterCommit() {
        VoucherCreateRequest request = new VoucherCreateRequest(
                "SALE20", VoucherType.PERCENT, BigDecimal.valueOf(20), false, null, null, true, null, null);
        when(voucherRepository.findByCodeIgnoreCase("SALE20")).thenReturn(Optional.empty());
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> {
            Voucher saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            adminVoucherService.createVoucher(request);

            // push must NOT have fired yet — it's deferred
            verify(sapoVoucherSyncService, never()).pushVoucher(anyLong());

            // simulate the transaction committing
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            // now it should have fired
            verify(sapoVoucherSyncService).pushVoucher(2L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteVoucher_WithinActiveTransaction_DefersDeactivateUntilAfterCommit() {
        voucher.setSapoPriceRuleId(501L);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(voucher));

        TransactionSynchronizationManager.initSynchronization();
        try {
            adminVoucherService.deleteVoucher(1L);

            // deactivate must NOT have fired yet — it's deferred
            verify(sapoVoucherSyncService, never()).deactivateVoucher(any());

            // simulate the transaction committing
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            // now it should have fired
            verify(sapoVoucherSyncService).deactivateVoucher(501L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
