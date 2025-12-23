package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdminLoginActivityResponse;
import com.fashionvista.backend.dto.AdminLoginActivityStatsResponse;
import com.fashionvista.backend.service.AdminLoginActivityService;
import com.fashionvista.backend.service.LoginActivityService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/login-activities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLoginActivityController {

    private final AdminLoginActivityService adminLoginActivityService;
    private final LoginActivityService loginActivityService;

    @GetMapping
    public Page<AdminLoginActivityResponse> getHistory(
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) Boolean loginSuccess,
        @RequestParam(required = false) String ipAddress,
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

        return adminLoginActivityService.getHistory(userId, loginSuccess, ipAddress, start, end, pageable);
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminLoginActivityStatsResponse> getStats(
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    ) {
        LocalDateTime start = startDate != null && !startDate.isEmpty()
            ? LocalDateTime.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            : null;
        LocalDateTime end = endDate != null && !endDate.isEmpty()
            ? LocalDateTime.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            : null;

        return ResponseEntity.ok(adminLoginActivityService.getStats(start, end));
    }

    @GetMapping("/logging-enabled")
    public ResponseEntity<Map<String, Object>> getLoggingEnabled() {
        Map<String, Object> body = new HashMap<>();
        body.put("enabled", loginActivityService.isLoginActivityEnabled());
        return ResponseEntity.ok(body);
    }

    @PutMapping("/logging-enabled")
    public ResponseEntity<Map<String, Object>> updateLoggingEnabled(@RequestParam boolean enabled) {
        loginActivityService.setLoginActivityEnabled(enabled);
        Map<String, Object> body = new HashMap<>();
        body.put("enabled", loginActivityService.isLoginActivityEnabled());
        return ResponseEntity.ok(body);
    }
}

