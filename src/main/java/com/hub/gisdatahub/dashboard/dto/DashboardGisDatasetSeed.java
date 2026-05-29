package com.hub.gisdatahub.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 대시보드 노출 단위 데이터셋을 sd_dashboard_dataset에 upsert하기 위한 seed DTO입니다.
public class DashboardGisDatasetSeed {

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
    private String metadata;
}
