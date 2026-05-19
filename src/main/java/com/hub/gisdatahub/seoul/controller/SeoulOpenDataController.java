package com.hub.gisdatahub.seoul.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.seoul.dto.DistrictLivingPopulationResponseDto;
import com.hub.gisdatahub.seoul.dto.RealtimeCityAirResponseDto;
import com.hub.gisdatahub.seoul.service.SeoulOpenDataService;

/**
 * [Controller] SeoulOpenDataController
 * React(프론트)에서 호출하는 서울 열린데이터광장 프록시 API입니다.
 *
 * 처리 흐름:
 *   HTTP 요청 → SeoulOpenDataService → SeoulOpenApiClient(호출) → SeoulOpenDataParser(파싱) → DTO JSON 응답
 *
 * 인증: SecurityConfig에서 GET /api/open-data/** 는 permitAll (JWT 불필요)
 */
@RestController
@RequestMapping("/api/open-data")
public class SeoulOpenDataController {

    private final SeoulOpenDataService seoulOpenDataService;

    public SeoulOpenDataController(SeoulOpenDataService seoulOpenDataService) {
        this.seoulOpenDataService = seoulOpenDataService;
    }

    /**
     * 실시간 도시 대기환경 (RealtimeCityAir).
     * GET /api/open-data/realtime-city-air
     */
    @GetMapping("/realtime-city-air")
    public RealtimeCityAirResponseDto getRealtimeCityAir() {
        return seoulOpenDataService.getRealtimeCityAir();
    }

    /**
     * 자치구 생활인구 (설정된 serviceName, 기본 SPOP_LOCAL_RESD_PPLTN).
     * GET /api/open-data/district-living-population?baseDate=yyyyMMdd
     *
     * @param baseDate 기준일 (생략 시 서비스에서 5일 전 날짜 사용)
     */
    @GetMapping("/district-living-population")
    public DistrictLivingPopulationResponseDto getDistrictLivingPopulation(
            @RequestParam(required = false) String baseDate) {
        return seoulOpenDataService.getDistrictLivingPopulation(baseDate);
    }

}
