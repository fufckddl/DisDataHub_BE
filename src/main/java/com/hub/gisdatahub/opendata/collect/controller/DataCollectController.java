package com.hub.gisdatahub.opendata.collect.controller;

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

    // 서울 인구 정보 가져오기
    @GetMapping("/living-population/dong")
    public String getLivingPopulationByDong(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "00") String hour,
        @RequestParam String areaCode
    ){
        return dataCollectService.getLivingPopulationByDong(date, hour, areaCode);
    }

    // 서울 자치구 인구 정보 가져오기
    @GetMapping("/living-population/sigungu")
    public String getLivingPopulationBySigungu(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "00") String hour,
        @RequestParam String sigunguCode
    ){
        return dataCollectService.getLivingPopulationBySigungu(date, hour, sigunguCode);
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
