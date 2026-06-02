package com.hub.gisdatahub.dashboard.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.dashboard.dto.AreaNavigationResponse;
import com.hub.gisdatahub.dashboard.dto.AreaPopulationChartResponse;
import com.hub.gisdatahub.dashboard.dto.DashboardGisDataSourceResponse;
import com.hub.gisdatahub.dashboard.dto.DashboardGisDatasetResponse;
import com.hub.gisdatahub.dashboard.dto.DashboardGisMetricResponse;
import com.hub.gisdatahub.dashboard.dto.DashboardGisRegionStatsResponse;
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

    @GetMapping("/area-navigation")
    public AreaNavigationResponse getAreaNavigation(@RequestParam String areaCode) {
        return dashboardBoundaryService.getAreaNavigation(areaCode);
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


    @GetMapping("/gis-data-sources")
    public List<DashboardGisDataSourceResponse> getDashboardGisDataSources(
        @RequestParam(required = false) String sourceCategory,
        @RequestParam(required = false) Integer priority,
        @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        return dashboardBoundaryService.getDashboardGisDataSources(sourceCategory, priority, activeOnly);
    }

    @GetMapping("/gis-datasets")
    public List<DashboardGisDatasetResponse> getDashboardGisDatasets(
        @RequestParam(required = false) String sourceCode,
        @RequestParam(required = false) String layerType,
        @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        return dashboardBoundaryService.getDashboardGisDatasets(sourceCode, layerType, activeOnly);
    }

    @GetMapping("/gis-metrics")
    public List<DashboardGisMetricResponse> getDashboardGisMetrics(
        @RequestParam(required = false) String sourceCode,
        @RequestParam(required = false) String datasetCode
    ) {
        return dashboardBoundaryService.getDashboardGisMetrics(sourceCode, datasetCode);
    }

    @GetMapping(value = "/gis-features", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getDashboardGisFeatures(
        @RequestParam String datasetCode,
        @RequestParam(required = false) String bbox,
        @RequestParam(required = false) String areaCode,
        @RequestParam(defaultValue = "500") int limit
    ) {
        return dashboardBoundaryService.getDashboardGisFeatures(datasetCode, bbox, areaCode, limit);
    }

    @GetMapping("/gis-region-stats")
    public DashboardGisRegionStatsResponse getDashboardGisRegionStats(
        @RequestParam String datasetCode
    ) {
        return dashboardBoundaryService.getDashboardGisRegionStats(datasetCode);
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
