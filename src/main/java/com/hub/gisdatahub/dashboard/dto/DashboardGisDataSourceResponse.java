package com.hub.gisdatahub.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 대시보드가 노출 가능한 GIS/통계 데이터 원천 목록 응답입니다.
public class DashboardGisDataSourceResponse {

    private String sourceCode;
    private String sourceName;
    private String providerName;
    private String providerType;
    private String sourceCategory;
    private String officialUrl;
    private String apiEndpoint;
    private String apiType;
    private String dataFormat;
    private String authType;
    private String spatialCoverage;
    private String spatialGranularity;
    private String temporalGranularity;
    private String updateCycle;
    private String coordinateSystem;
    private Boolean hasGeometry;
    private Boolean hasPointCoordinate;
    private String collectionDifficulty;
    private Integer priority;
    private String verificationStatus;
    private Boolean isActive;
    private Integer datasetCount;
    private Integer metricCount;
}
