package com.hub.gisdatahub.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Markdown 후보표를 신규 대시보드 GIS 카탈로그 테이블에 반영한 결과입니다.
public class DashboardGisCatalogSeedResult {

    private int parsedSourceCount;
    private int upsertedSourceCount;
    private int upsertedDatasetCount;
    private int upsertedMetricCount;
}
