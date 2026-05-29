package com.hub.gisdatahub.opendata.collect.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.dashboard.dto.DashboardGisCatalogSeedResult;
import com.hub.gisdatahub.opendata.collect.service.DashboardGisCatalogSeedService;
import com.hub.gisdatahub.opendata.collect.service.DataCollectService;

@RestController
@RequestMapping("/api/opendata/collect")
public class DataCollectController {
    
    @Autowired
    private DataCollectService dataCollectService;

    @Autowired
    private DashboardGisCatalogSeedService dashboardGisCatalogSeedService;

    // 스케줄러와 동일한 방식으로 행안부 주민등록 인구를 OpenAPI에서 수집해 DB에 저장합니다.
    @PostMapping("/living-population/sigungu/collect")
    public ResponseEntity<Map<String, Object>> collectResidentPopulation(
        @RequestParam(required = false) String statsYm,
        @RequestParam(defaultValue = "1") String regSeCd,
        @RequestParam(required = false) String lv,
        @RequestParam(required = false) String sidoCode
    ) {
        int savedCount = dataCollectService.collectResidentPopulation(statsYm, regSeCd, lv, sidoCode);
        return ResponseEntity.ok(Map.of(
            "target", "MOIS_ADMM_SEXD_AGE_PPLTN",
            "statsYm", statsYm == null ? "" : statsYm,
            "regSeCd", regSeCd,
            "lv", lv == null ? "ALL" : lv,
            "sidoCode", sidoCode == null ? "" : sidoCode,
            "savedCount", savedCount
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
