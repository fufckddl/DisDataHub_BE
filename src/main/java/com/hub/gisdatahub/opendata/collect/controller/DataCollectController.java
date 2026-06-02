package com.hub.gisdatahub.opendata.collect.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.dashboard.dto.DashboardGisCatalogSeedResult;
import com.hub.gisdatahub.opendata.collect.service.DashboardBoundaryCacheService;
import com.hub.gisdatahub.opendata.collect.service.DashboardGisCatalogSeedService;
import com.hub.gisdatahub.opendata.collect.service.DashboardGisObservationSyncService;
import com.hub.gisdatahub.opendata.collect.service.DashboardGisOpenApiCollectService;
import com.hub.gisdatahub.opendata.collect.service.DataCollectService;

@RestController
@RequestMapping("/api/opendata/collect")
public class DataCollectController {
    
    @Autowired
    private DataCollectService dataCollectService;

    @Autowired
    private DashboardGisCatalogSeedService dashboardGisCatalogSeedService;

    @Autowired
    private DashboardBoundaryCacheService dashboardBoundaryCacheService;

    @Autowired
    private DashboardGisObservationSyncService dashboardGisObservationSyncService;

    @Autowired
    private DashboardGisOpenApiCollectService dashboardGisOpenApiCollectService;

    // 스케줄러와 동일한 방식으로 행안부 주민등록 인구를 OpenAPI에서 수집해 DB에 저장합니다.
    @PostMapping("/living-population/sigungu/collect")
    public ResponseEntity<Map<String, Object>> collectResidentPopulation(
        @RequestParam(required = false) String statsYm,
        @RequestParam(defaultValue = "1") String regSeCd,
        @RequestParam(required = false) String lv,
        @RequestParam(required = false) String sidoCode,
        @RequestParam(defaultValue = "false") boolean syncDashboard
    ) {
        int savedCount = dataCollectService.collectResidentPopulation(statsYm, regSeCd, lv, sidoCode);
        Map<String, Object> dashboardSyncResult = syncDashboard
                ? dashboardGisObservationSyncService.syncResidentPopulation(statsYm, regSeCd)
                : Map.of();
        return ResponseEntity.ok(Map.of(
            "target", "MOIS_ADMM_SEXD_AGE_PPLTN",
            "statsYm", statsYm == null ? "" : statsYm,
            "regSeCd", regSeCd,
            "lv", lv == null ? "ALL" : lv,
            "sidoCode", sidoCode == null ? "" : sidoCode,
            "savedCount", savedCount,
            "dashboardSyncResult", dashboardSyncResult
        ));
    }

    // 스케줄러와 동일한 방식으로 서울 S-DoT 유동인구를 OpenAPI에서 수집해 DB에 저장합니다.
    @PostMapping("/sdot/visitor/collect")
    public ResponseEntity<Map<String, Object>> collectSdotVisitorCount(
        @RequestParam(defaultValue = "1") int start,
        @RequestParam(defaultValue = "1000") int end
    ) {
        int savedCount = dataCollectService.collectSdotVisitorCount(start, end);
        return ResponseEntity.ok(Map.of(
            "target", "IotVdata018",
            "savedCount", savedCount
        ));
    }


    // SQL로 생성한 대시보드 GIS 카탈로그 테이블에 Markdown 후보표를 등록/갱신합니다.
    @PostMapping("/dashboard-gis/catalog/seed")
    public ResponseEntity<DashboardGisCatalogSeedResult> seedDashboardGisCatalog() {
        return ResponseEntity.ok(dashboardGisCatalogSeedService.seedCandidateCatalog());
    }

    // 전국 최초 로딩 성능을 위해 시군구 경계를 광역시도 경계로 캐시합니다.
    @PostMapping("/dashboard-gis/boundaries/sido/cache")
    public ResponseEntity<Map<String, Object>> refreshSidoBoundaryCache() {
        return ResponseEntity.ok(dashboardBoundaryCacheService.refreshSidoBoundaryCache());
    }

    // MOIS 주민등록 인구 원천 테이블을 대시보드 GIS 관측값 테이블로 동기화합니다.
    @PostMapping("/dashboard-gis/resident-population/sync")
    public ResponseEntity<Map<String, Object>> syncDashboardResidentPopulation(
        @RequestParam(required = false) String statsYm,
        @RequestParam(defaultValue = "1") String regSeCd
    ) {
        return ResponseEntity.ok(dashboardGisObservationSyncService.syncResidentPopulation(statsYm, regSeCd));
    }

    // 대시보드 GIS 카탈로그 원천을 공통 OpenAPI 수집기로 호출하고 공통 저장 테이블에 저장합니다.
    @RequestMapping(value = "/dashboard-gis/collect", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> collectDashboardGisOpenApi(
        @RequestParam(required = false) String sourceCode,
        @RequestParam(defaultValue = "1") Integer pageNo,
        @RequestParam(defaultValue = "5") Integer numOfRows,
        @RequestParam(required = false) String statsYm,
        @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(dashboardGisOpenApiCollectService.collect(
                sourceCode,
                pageNo,
                numOfRows,
                statsYm,
                keyword));
    }

    // 전기차 충전소 지도 피처는 최대 500건만 표시하고, 통계는 원천 API 전체 건수(totalCount)를 시도별로 저장합니다.
    @RequestMapping(value = "/dashboard-gis/ev-charger/region-stats/collect", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> collectEvChargerRegionStats() {
        return ResponseEntity.ok(dashboardGisOpenApiCollectService.collectEvChargerRegionStats());
    }

    // 현재 공통 저장 테이블 기준으로 데이터셋별 수집 완료/미완료 상태를 확인합니다.
    @GetMapping("/dashboard-gis/collect/status")
    public ResponseEntity<Map<String, Object>> getDashboardGisCollectStatus() {
        return ResponseEntity.ok(Map.of("results", dashboardGisOpenApiCollectService.collectStatus()));
    }

    // 서울 유동 인구
    @GetMapping("/sdot/visitor")
    public String getSdotVisitorCount(
        @RequestParam(defaultValue = "1") int start,
        @RequestParam(defaultValue = "2") int end,
        @RequestParam(required = false) String district,
        @RequestParam(required = false) String date
    ){
        return dataCollectService.getSdotVisitorCount(start, end, district, date);
    }

}
