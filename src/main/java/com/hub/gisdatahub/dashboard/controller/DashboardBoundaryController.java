package com.hub.gisdatahub.dashboard.controller;

import java.util.List;
import java.util.Map;

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
            @RequestParam(value = "level", defaultValue = "SIGUNGU") String level,
            @RequestParam(value = "sidoCode", required = false) String sidoCode,
            @RequestParam(value = "parentAreaCode", required = false) String parentAreaCode,
            @RequestParam(value = "bbox", required = false) String bbox) {
        return dashboardBoundaryService.getAreaBoundaries(level, sidoCode, parentAreaCode, bbox);
    }

    @GetMapping(value = "/area-boundary-cache", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getAreaBoundaryCache(
            @RequestParam(value = "levels", defaultValue = "SIDO,SIGUNGU,EUPMYEONDONG") String levels) {
        return dashboardBoundaryService.getAreaBoundaryCache(levels);
    }

    @GetMapping("/area-navigation")
    public AreaNavigationResponse getAreaNavigation(@RequestParam("areaCode") String areaCode) {
        return dashboardBoundaryService.getAreaNavigation(areaCode);
    }

    @GetMapping("/population")
    public AreaPopulationChartResponse getAreaPopulation(
        @RequestParam("areaCode") String areaCode,
        @RequestParam(value = "areaLevel", required = false) String areaLevel,
        @RequestParam(value = "date", required = false) String date,
        @RequestParam(value = "hour", required = false) String hour
    ){
        return dashboardBoundaryService.getAreaPopulation(areaCode, areaLevel, date, hour);
    }


    @GetMapping("/gis-data-sources")
    public List<DashboardGisDataSourceResponse> getDashboardGisDataSources(
        @RequestParam(value = "sourceCategory", required = false) String sourceCategory,
        @RequestParam(value = "priority", required = false) Integer priority,
        @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly
    ) {
        return dashboardBoundaryService.getDashboardGisDataSources(sourceCategory, priority, activeOnly);
    }

    @GetMapping("/gis-datasets")
    public List<DashboardGisDatasetResponse> getDashboardGisDatasets(
        @RequestParam(value = "sourceCode", required = false) String sourceCode,
        @RequestParam(value = "layerType", required = false) String layerType,
        @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly
    ) {
        return dashboardBoundaryService.getDashboardGisDatasets(sourceCode, layerType, activeOnly);
    }

    @GetMapping("/gis-metrics")
    public List<DashboardGisMetricResponse> getDashboardGisMetrics(
        @RequestParam(value = "sourceCode", required = false) String sourceCode,
        @RequestParam(value = "datasetCode", required = false) String datasetCode
    ) {
        return dashboardBoundaryService.getDashboardGisMetrics(sourceCode, datasetCode);
    }

    @GetMapping(value = "/gis-features", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getDashboardGisFeatures(
        @RequestParam("datasetCode") String datasetCode,
        @RequestParam(value = "bbox", required = false) String bbox,
        @RequestParam(value = "areaCode", required = false) String areaCode,
        @RequestParam(value = "limit", defaultValue = "500") int limit
    ) {
        return dashboardBoundaryService.getDashboardGisFeatures(datasetCode, bbox, areaCode, limit);
    }

    @GetMapping("/gis-region-stats")
    public DashboardGisRegionStatsResponse getDashboardGisRegionStats(
        @RequestParam("datasetCode") String datasetCode
    ) {
        return dashboardBoundaryService.getDashboardGisRegionStats(datasetCode);
    }

    @GetMapping("/gis-observations")
    public Map<String, Object> getDashboardGisObservations(
        @RequestParam("datasetCode") String datasetCode,
        @RequestParam(value = "areaCode", required = false) String areaCode,
        @RequestParam(value = "limit", defaultValue = "12") int limit
    ) {
        return dashboardBoundaryService.getDashboardGisObservations(datasetCode, areaCode, limit);
    }

    @GetMapping("/floating-population")
    public FloatingPopulationChartResponse getFloatingPopulation(
        @RequestParam("areaCode") String areaCode,
        @RequestParam(value = "areaLevel", required = false) String areaLevel,
        @RequestParam(value = "date", required = false) String date,
        @RequestParam(value = "hour", required = false) String hour
    ){
        return dashboardBoundaryService.getFloatingPopulation(areaCode, areaLevel, date, hour);
    }
}
