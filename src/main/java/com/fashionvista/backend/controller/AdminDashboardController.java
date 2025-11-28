package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AdminOverviewResponse;
import com.fashionvista.backend.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/overview")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public AdminOverviewResponse getOverview() {
        return adminDashboardService.getOverview();
    }
}


