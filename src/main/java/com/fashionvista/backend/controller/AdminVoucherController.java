package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdminVoucherResponse;
import com.fashionvista.backend.dto.VoucherCreateRequest;
import com.fashionvista.backend.dto.VoucherUpdateRequest;
import com.fashionvista.backend.service.AdminVoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVoucherController {

    private final AdminVoucherService adminVoucherService;

    @GetMapping
    public Page<AdminVoucherResponse> getAllVouchers(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Boolean active,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return adminVoucherService.getAllVouchers(search, active, pageable);
    }

    @GetMapping("/{id}")
    public AdminVoucherResponse getVoucherById(@PathVariable Long id) {
        return adminVoucherService.getVoucherById(id);
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public AdminVoucherResponse createVoucher(@RequestBody @Valid VoucherCreateRequest request) {
        return adminVoucherService.createVoucher(request);
    }

    @PatchMapping("/{id}")
    public AdminVoucherResponse updateVoucher(
        @PathVariable Long id,
        @RequestBody @Valid VoucherUpdateRequest request
    ) {
        return adminVoucherService.updateVoucher(id, request);
    }

    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVoucher(@PathVariable Long id) {
        adminVoucherService.deleteVoucher(id);
    }
}

