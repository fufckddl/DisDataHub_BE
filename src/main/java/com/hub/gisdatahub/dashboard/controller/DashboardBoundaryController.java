package com.hub.gisdatahub.dashboard.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.dashboard.service.DashboardBoundaryService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardBoundaryController {

    private final DashboardBoundaryService dashboardBoundaryService;

    public DashboardBoundaryController(DashboardBoundaryService dashboardBoundaryService) {
        this.dashboardBoundaryService = dashboardBoundaryService;
    }

    @GetMapping(value = "/area-boundaries", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getAreaBoundaries(
            @RequestParam(defaultValue = "SIGUNGU") String level,
            @RequestParam(required = false) String sidoCode,
            @RequestParam(required = false) String bbox) {
        return dashboardBoundaryService.getAreaBoundaries(level, sidoCode, bbox);
    }
}
