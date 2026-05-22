package com.hub.gisdatahub.dashboard.controller;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.hub.gisdatahub.dashboard.dto.AreaPopulationChartResponse;
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
    @GetMapping("/population")
    public AreaPopulationChartResponse getAreaPopulation(
        @RequestParam String areaCode,
        @RequestParam(required = false) String date,
        @RequestParam(required = false) String hour
    ){
        // TODO: sd_area_population 조회 Service/Repository 구현 후 실제 Chart.js 응답 DTO를 반환하도록 교체합니다.
        throw new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "sd_area_population 조회 서비스 구현이 필요합니다."
        );
    }
}
