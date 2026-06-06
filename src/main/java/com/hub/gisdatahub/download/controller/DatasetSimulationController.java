package com.hub.gisdatahub.download.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.download.dto.PointRadiusSimulationRequestDto;
import com.hub.gisdatahub.download.dto.PointRadiusSimulationResponseDto;
import com.hub.gisdatahub.download.dto.SimulationAreaMeasureRequestDto;
import com.hub.gisdatahub.download.dto.SimulationAreaMeasureResponseDto;
import com.hub.gisdatahub.download.service.DatasetSimulationService;

@RestController
@RequestMapping("/api/download/simulation")
public class DatasetSimulationController {

    private final DatasetSimulationService datasetSimulationService;

    public DatasetSimulationController(DatasetSimulationService datasetSimulationService) {
        this.datasetSimulationService = datasetSimulationService;
    }

    @PostMapping("/datasets/{datasetId}/point-radius")
    public PointRadiusSimulationResponseDto runPointRadiusSimulation(
            @PathVariable("datasetId") Long datasetId,
            @RequestBody PointRadiusSimulationRequestDto requestDto,
            Authentication authentication
    ) {
        Integer userId = resolveAuthenticatedUserId(authentication);
        return datasetSimulationService.runPointRadiusSimulation(datasetId, requestDto, userId);
    }

    @PostMapping("/datasets/{datasetId}/measure-area")
    public SimulationAreaMeasureResponseDto measurePolygonArea(
            @PathVariable("datasetId") Long datasetId,
            @RequestBody SimulationAreaMeasureRequestDto requestDto,
            Authentication authentication
    ) {
        Integer userId = resolveAuthenticatedUserId(authentication);
        return datasetSimulationService.measurePolygonArea(datasetId, requestDto, userId);
    }

    private Integer resolveAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof String principalValue)) {
            return null;
        }

        if ("anonymousUser".equals(principalValue)) {
            return null;
        }

        return Integer.parseInt(principalValue);
    }
}
