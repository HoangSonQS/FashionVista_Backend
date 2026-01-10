package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdminCartListResponse;
import com.fashionvista.backend.service.AdminCartService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/carts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')") // Adjust roles as needed
public class AdminCartController {

    private final AdminCartService adminCartService;

    @GetMapping
    public ResponseEntity<Page<AdminCartListResponse>> getCarts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isAbandoned,
            @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminCartService.getAdminCarts(search, isAbandoned, pageable));
    }

    @PostMapping("/{id}/remind")
    public ResponseEntity<Void> sendReminder(@PathVariable Long id) {
        adminCartService.sendCartReminder(id);
        return ResponseEntity.ok().build();
    }
}
