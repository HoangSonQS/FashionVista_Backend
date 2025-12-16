package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.ReturnRequestResponse;
import com.fashionvista.backend.dto.UpdateReturnStatusRequest;
import com.fashionvista.backend.entity.ReturnStatus;
import com.fashionvista.backend.service.AdminReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/returns")
@RequiredArgsConstructor
public class AdminReturnController {

    private final AdminReturnService adminReturnService;

    @GetMapping
    public Page<ReturnRequestResponse> getAll(
        @RequestParam(required = false) ReturnStatus status,
        Pageable pageable
    ) {
        return adminReturnService.getAll(status, pageable);
    }

    @GetMapping("/by-order/{orderId}")
    public ReturnRequestResponse getByOrder(@PathVariable Long orderId) {
        return adminReturnService.getByOrder(orderId);
    }

    @PatchMapping("/{id}")
    public ReturnRequestResponse updateStatus(
        @PathVariable Long id,
        @RequestBody @Valid UpdateReturnStatusRequest request
    ) {
        return adminReturnService.updateStatus(id, request);
    }
}


