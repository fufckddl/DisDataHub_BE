package com.hub.gisdatahub.download.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hub.gisdatahub.download.dto.DownloadDatasetDetailDto;
import com.hub.gisdatahub.download.dto.PointRadiusSimulationRequestDto;
import com.hub.gisdatahub.download.dto.PointRadiusSimulationResponseDto;
import com.hub.gisdatahub.download.dto.PointRadiusSimulationSummaryDto;
import com.hub.gisdatahub.download.dto.SimulationAreaMeasureRequestDto;
import com.hub.gisdatahub.download.dto.SimulationAreaMeasureResponseDto;
import com.hub.gisdatahub.download.dto.SimulationMeasurePointDto;
import com.hub.gisdatahub.download.mapper.DatasetSimulationMapper;

@Service
public class DatasetSimulationService {

    private final DatasetSimulationMapper datasetSimulationMapper;

    public DatasetSimulationService(DatasetSimulationMapper datasetSimulationMapper) {
        this.datasetSimulationMapper = datasetSimulationMapper;
    }

    public PointRadiusSimulationResponseDto runPointRadiusSimulation(
            Long datasetId,
            PointRadiusSimulationRequestDto requestDto,
            Integer userId
    ) {
        DownloadDatasetDetailDto dataset = datasetSimulationMapper.findDatasetDetailById(datasetId);
        if (dataset == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "데이터셋을 찾을 수 없습니다.");
        }

        validateDatasetDetailAccess(dataset, userId);
        validatePointSimulationRequest(dataset, requestDto);

        Integer radius = requestDto.getRadius();
        PointRadiusSimulationSummaryDto summary =
                datasetSimulationMapper.findPointRadiusSimulationSummary(datasetId, radius);

        if (summary == null || summary.getTotalPointCount() == null || summary.getTotalPointCount() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "시뮬레이션할 Point 공간 데이터가 없습니다.");
        }

        PointRadiusSimulationResponseDto response = new PointRadiusSimulationResponseDto();
        response.setSummary(summary);
        response.setTable(datasetSimulationMapper.findPointRadiusSimulationTable(datasetId, radius, 5));

        String resultGeoJson = datasetSimulationMapper.findPointRadiusSimulationGeoJson(datasetId, radius, 5);
        response.setResultGeoJson(
                resultGeoJson != null ? resultGeoJson : "{\"type\":\"FeatureCollection\",\"features\":[]}"
        );

        return response;
    }

    public SimulationAreaMeasureResponseDto measurePolygonArea(
            Long datasetId,
            SimulationAreaMeasureRequestDto requestDto,
            Integer userId
    ) {
        DownloadDatasetDetailDto dataset = datasetSimulationMapper.findDatasetDetailById(datasetId);
        if (dataset == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "데이터셋을 찾을 수 없습니다.");
        }

        validateDatasetDetailAccess(dataset, userId);
        validateAreaMeasurementRequest(dataset, requestDto);

        String polygonWkt = buildClosedPolygonWkt(requestDto.getPoints());
        Double areaSquareMeters = datasetSimulationMapper.calculatePolygonAreaSquareMeters(polygonWkt);

        if (areaSquareMeters == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "면적 계산 결과를 가져오지 못했습니다.");
        }

        return new SimulationAreaMeasureResponseDto(areaSquareMeters);
    }

    private void validateDatasetDetailAccess(DownloadDatasetDetailDto dataset, Integer userId) {
        if (Boolean.TRUE.equals(dataset.getIsPublic())) {
            return;
        }

        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비공개 데이터셋은 로그인 후 접근할 수 있습니다.");
        }

        String userOrganization = datasetSimulationMapper.findUserOrganization(userId);
        String datasetOwnerOrganization = datasetSimulationMapper.findDatasetOwnerOrganization(dataset.getDatasetId());

        if (!Objects.equals(normalize(userOrganization), normalize(datasetOwnerOrganization))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "동일 소속기관 사용자만 접근할 수 있습니다.");
        }
    }

    private void validatePointSimulationRequest(
            DownloadDatasetDetailDto dataset,
            PointRadiusSimulationRequestDto requestDto
    ) {
        if (requestDto == null || requestDto.getRadius() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "반경(radius)은 필수입니다.");
        }

        if (requestDto.getRadius() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "반경(radius)은 0보다 커야 합니다.");
        }

        if (!Boolean.TRUE.equals(dataset.getIsSpatial())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공간 데이터셋만 시뮬레이션할 수 있습니다.");
        }

        String spatialType = dataset.getSpatialType() == null
                ? ""
                : dataset.getSpatialType().trim().toUpperCase(Locale.ROOT);

        if (!spatialType.contains("POINT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Point 타입 데이터셋만 1차 시뮬레이션 대상입니다.");
        }
    }

    private void validateAreaMeasurementRequest(
            DownloadDatasetDetailDto dataset,
            SimulationAreaMeasureRequestDto requestDto
    ) {
        if (!Boolean.TRUE.equals(dataset.getIsSpatial())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공간 데이터셋에서만 면적을 계산할 수 있습니다.");
        }

        if (requestDto == null || requestDto.getPoints() == null || requestDto.getPoints().size() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "면적 계산을 위해서는 세 개 이상의 좌표가 필요합니다.");
        }

        for (SimulationMeasurePointDto point : requestDto.getPoints()) {
            if (point == null || point.getLat() == null || point.getLng() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "좌표 정보가 올바르지 않습니다.");
            }
        }
    }

    private String buildClosedPolygonWkt(List<SimulationMeasurePointDto> points) {
        StringJoiner joiner = new StringJoiner(", ");

        for (SimulationMeasurePointDto point : points) {
            joiner.add(point.getLng() + " " + point.getLat());
        }

        SimulationMeasurePointDto firstPoint = points.get(0);
        SimulationMeasurePointDto lastPoint = points.get(points.size() - 1);

        if (!Objects.equals(firstPoint.getLat(), lastPoint.getLat())
                || !Objects.equals(firstPoint.getLng(), lastPoint.getLng())) {
            joiner.add(firstPoint.getLng() + " " + firstPoint.getLat());
        }

        return "POLYGON((" + joiner + "))";
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }
}
