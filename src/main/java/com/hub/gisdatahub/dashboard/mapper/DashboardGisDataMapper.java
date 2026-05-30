package com.hub.gisdatahub.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.dashboard.dto.DashboardGisDataSourceSeed;
import com.hub.gisdatahub.dashboard.dto.DashboardGisDatasetSeed;
import com.hub.gisdatahub.dashboard.dto.DashboardGisMetricSeed;

@Mapper
public interface DashboardGisDataMapper {

    int upsertDataSource(DashboardGisDataSourceSeed row);

    int upsertDataset(DashboardGisDatasetSeed row);

    int upsertMetric(DashboardGisMetricSeed row);
}
