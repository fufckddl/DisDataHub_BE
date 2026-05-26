package com.hub.gisdatahub.opendata.collect.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.opendata.collect.service.DataCollectService;

@RestController
@RequestMapping("/api/opendata/collect")
public class DataCollectController {
    
    @Autowired
    private DataCollectService dataCollectService;

    // 스케줄러와 동일한 방식으로 서울 자치구 생활인구를 OpenAPI에서 수집해 DB에 저장합니다.
    @PostMapping("/living-population/sigungu/collect")
    public ResponseEntity<Map<String, Object>> collectLivingPopulationBySigungu(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "00") String hour
    ) {
        int savedCount = dataCollectService.collectSeoulSigunguLivingPopulation(date, hour);
        return ResponseEntity.ok(Map.of(
            "target", "SPOP_LOCAL_RESD_JACHI",
            "savedCount", savedCount
        ));
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
