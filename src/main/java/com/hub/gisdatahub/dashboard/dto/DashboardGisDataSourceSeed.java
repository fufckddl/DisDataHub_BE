package com.hub.gisdatahub.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 대시보드 GIS 후보 원천을 sd_dashboard_data_source에 upsert하기 위한 seed DTO입니다.
public class DashboardGisDataSourceSeed {

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
    private String licenseNote;
    private String quotaNote;
    private Integer retentionDays;
    private String verificationStatus;
    private String verificationNote;
    private String metadata;
}
