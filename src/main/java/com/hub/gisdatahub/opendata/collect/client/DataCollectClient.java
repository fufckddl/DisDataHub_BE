package com.hub.gisdatahub.opendata.collect.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DataCollectClient {

    private static final String LIVING_POPULATION_DONG = "SPOP_LOCAL_RESD_DONG";
    private static final String LIVING_POPULATION_SIGUNGU = "SPOP_LOCAL_RESD_JACHI";
    private static final String SDOT_VISITOR_COUNT = "IotVdata018";

    private final RestClient restClient;
    private final String key;


    public DataCollectClient(
        @Value("${seoul.open-api.base-url}") String baseUrl,
        @Value("${seoul.open-api.key}") String key
    ){
        this.key = key;
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
    }
    //서울시 인구 데이터(행정동) 내국인
    // 연령, 성별 전부 포함됨
    public String callLivingPopulationByDong(
        String date,
        String hour,
        String areaCode
    ) {
        String result = restClient.get()
                .uri("/{key}/json/{service}/1/5/{date}/{hour}/{areaCode}",
                        key,
                        LIVING_POPULATION_DONG,
                        date,
                        hour, 
                        areaCode)
                .retrieve()
                .body(String.class); // 리턴받을 클래스 타입 지정
        return result;
    }
    // 서울시 인구 데이터(자치구) 내국인
    // 구 단위 Polygon 클릭 시 사용
    public String callLivingPopulationBySigungu(
        String date,
        String hour,
        String sigunguCode
    ) {
        String result = restClient.get()
                .uri("/{key}/json/{service}/1/5/{date}/{hour}/{sigunguCode}",
                        key,
                        LIVING_POPULATION_SIGUNGU,
                        date,
                        hour,
                        sigunguCode)
                .retrieve()
                .body(String.class);
        return result;
    }
    // 서울시 S-DoT 유동인구 측정 정보
    public String callSdotVisitorCount(
        int start,
        int end,
        String district,
        String registeredDate
    ) {
        String result = restClient.get()
                .uri("/{key}/json/{service}/{start}/{end}/{district}/{registeredDate}",
                    key,
                    SDOT_VISITOR_COUNT,
                    start,
                    end,
                    district,
                    registeredDate
                )
                .retrieve()
                .body(String.class);

        return result;
    }
    // 서울시 S-DoT 전체 조회
    public String callSdotVisitorCount(
        int start,
        int end
    ) {
        String result = restClient.get()
                .uri("/{key}/json/{service}/{start}/{end}",
                    key,
                    SDOT_VISITOR_COUNT,
                    start,
                    end
                )
                .retrieve()
                .body(String.class);

        return result;
    }
    
}
