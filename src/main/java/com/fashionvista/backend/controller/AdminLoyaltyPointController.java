package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdjustLoyaltyPointsRequest;
import com.fashionvista.backend.dto.AdminLoyaltyPointHistoryResponse;
import com.fashionvista.backend.dto.AdminLoyaltyPointStatsResponse;
import com.fashionvista.backend.service.AdminLoyaltyPointService;
import com.fashionvista.backend.service.UserContextService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/loyalty-points")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLoyaltyPointController {

    private final AdminLoyaltyPointService adminLoyaltyPointService;
    private final UserContextService userContextService;

    @GetMapping("/history")
    public Page<AdminLoyaltyPointHistoryResponse> getHistory(
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String transactionType,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("ASC")
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        LocalDateTime start = startDate != null && !startDate.isEmpty()
            ? LocalDateTime.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            : null;
        LocalDateTime end = endDate != null && !endDate.isEmpty()
            ? LocalDateTime.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            : null;

        return adminLoyaltyPointService.getHistory(userId, transactionType, start, end, pageable);
    }

    @PostMapping("/adjust")
    public ResponseEntity<AdminLoyaltyPointHistoryResponse> adjustPoints(
        @RequestBody @Valid AdjustLoyaltyPointsRequest request
    ) {
        Long adminId = userContextService.getCurrentUser().getId();
        AdminLoyaltyPointHistoryResponse response = adminLoyaltyPointService.adjustPoints(request, adminId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminLoyaltyPointStatsResponse> getStats() {
        return ResponseEntity.ok(adminLoyaltyPointService.getStats());
    }
}

