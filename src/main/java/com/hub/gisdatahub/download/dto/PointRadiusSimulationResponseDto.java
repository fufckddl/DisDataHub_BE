package com.hub.gisdatahub.download.dto;

import java.util.List;

import lombok.Data;

@Data
public class PointRadiusSimulationResponseDto {
    private PointRadiusSimulationSummaryDto summary;
    private List<PointRadiusSimulationTableRowDto> table;
    private String resultGeoJson;
}
