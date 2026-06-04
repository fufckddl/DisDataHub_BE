package com.hub.gisdatahub.download.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.download.dto.DownloadDatasetDetailDto;
import com.hub.gisdatahub.download.dto.PointRadiusSimulationSummaryDto;
import com.hub.gisdatahub.download.dto.PointRadiusSimulationTableRowDto;

@Mapper
public interface DatasetSimulationMapper {

    DownloadDatasetDetailDto findDatasetDetailById(Long datasetId);

    String findDatasetOwnerOrganization(Long datasetId);

    String findUserOrganization(Integer userId);

    PointRadiusSimulationSummaryDto findPointRadiusSimulationSummary(
            @Param("datasetId") Long datasetId,
            @Param("radius") Integer radius
    );

    List<PointRadiusSimulationTableRowDto> findPointRadiusSimulationTable(
            @Param("datasetId") Long datasetId,
            @Param("radius") Integer radius,
            @Param("limit") Integer limit
    );

    String findPointRadiusSimulationGeoJson(
            @Param("datasetId") Long datasetId,
            @Param("radius") Integer radius,
            @Param("limit") Integer limit
    );
}
