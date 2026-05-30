package com.hub.gisdatahub.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 특정 원천에서 대시보드에 표시할 데이터셋 목록 응답입니다.
public class DashboardGisDatasetResponse {

    private String sourceCode;
    private String datasetCode;
    private String datasetName;
    private String dashboardLayerType;
    private String dashboardMetricHint;
    private String defaultGeometryType;
    private String defaultAreaLevel;
    private String spatialJoinStrategy;
    private String collectionPolicy;
    private Integer displayPriority;
    private Boolean isInitialCandidate;
    private Integer metricCount;
    private Integer observationCount;
    private Integer featureCount;
}
