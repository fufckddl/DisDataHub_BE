package com.hub.gisdatahub.download.dto;

import java.util.List;

import lombok.Data;

@Data
public class SimulationAreaMeasureRequestDto {

    private List<SimulationMeasurePointDto> points;
}
