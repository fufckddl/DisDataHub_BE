package com.hub.gisdatahub.admin.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.admin.dto.AdminDashboardSummaryDto;
import com.hub.gisdatahub.admin.service.AdminDashboardService;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    @Autowired
    private AdminDashboardService adminDashboardService;

    @GetMapping("summary")
    public Map<String, Object> summary() {

        Map<String, Object> response = new HashMap<>();

        AdminDashboardSummaryDto dashboardSummary =
            adminDashboardService.getDashboardSummary();

        response.put("dashboardSummary", dashboardSummary);
        response.put("result", "success");

        return response;
    }

}