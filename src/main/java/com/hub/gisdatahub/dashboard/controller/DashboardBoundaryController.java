package com.hub.gisdatahub.dashboard.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.dashboard.dto.AreaPopulationChartResponse;
import com.hub.gisdatahub.dashboard.dto.FloatingPopulationChartResponse;
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
            @RequestParam(required = false) String parentAreaCode,
            @RequestParam(required = false) String bbox) {
        return dashboardBoundaryService.getAreaBoundaries(level, sidoCode, parentAreaCode, bbox);
    }
    @GetMapping("/population")
    public AreaPopulationChartResponse getAreaPopulation(
        @RequestParam String areaCode,
        @RequestParam(required = false) String areaLevel,
        @RequestParam(required = false) String date,
        @RequestParam(required = false) String hour
    ){
        return dashboardBoundaryService.getAreaPopulation(areaCode, areaLevel, date, hour);
    }

    @GetMapping("/floating-population")
    public FloatingPopulationChartResponse getFloatingPopulation(
        @RequestParam String areaCode,
        @RequestParam(required = false) String areaLevel,
        @RequestParam(required = false) String date,
        @RequestParam(required = false) String hour
    ){
        return dashboardBoundaryService.getFloatingPopulation(areaCode, areaLevel, date, hour);
    }
}
