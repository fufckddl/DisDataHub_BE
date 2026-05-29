package com.hub.gisdatahub.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 대시보드 차트/툴팁 지표 사전을 sd_dashboard_metric에 upsert하기 위한 seed DTO입니다.
public class DashboardGisMetricSeed {

    private String datasetCode;
    private String metricCode;
    private String metricName;
    private String valueType;
    private String unit;
    private String chartGroup;
    private Integer sortOrder;
    private Boolean isDefault;
    private String metadata;
}
