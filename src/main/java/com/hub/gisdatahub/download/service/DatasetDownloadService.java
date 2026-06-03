package com.hub.gisdatahub.download.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.download.dto.DatasetDownloadPageDto;
import com.hub.gisdatahub.download.dto.DatasetFeatureExportDto;
import com.hub.gisdatahub.download.dto.DatasetStatDto;
import com.hub.gisdatahub.download.dto.DatasetViewLogDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetDetailDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetFileDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetListItemDto;
import com.hub.gisdatahub.download.dto.DownloadExportResultDto;
import com.hub.gisdatahub.download.dto.DownloadLogDto;
import com.hub.gisdatahub.download.mapper.DatasetDownloadMapper;
import com.hub.gisdatahub.s3.dto.S3DownloadResult;
import com.hub.gisdatahub.s3.service.S3FileService;
import com.hub.gisdatahub.user.mapper.UserMapper;

@Service
public class DatasetDownloadService {

    private final DatasetDownloadMapper datasetDownloadMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final S3FileService s3FileService;

    public DatasetDownloadService(
            DatasetDownloadMapper datasetDownloadMapper,
            UserMapper userMapper,
            S3FileService s3FileService
    ) {
        this.datasetDownloadMapper = datasetDownloadMapper;
        this.s3FileService = s3FileService;
    }

    // 메인페이지 데이터셋 목록
    public List<DownloadDatasetListItemDto> getApprovedDownloadDatasetList() {
        return datasetDownloadMapper.findApprovedDownloadDatasetList();
    }

    public DatasetDownloadPageDto getDatasetDownloadPage(Long datasetId, Integer userId, String viewIp) {
        DownloadDatasetDetailDto dataset = datasetDownloadMapper.findDatasetDetailById(datasetId);

        if (dataset == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "데이터셋을 찾을 수 없습니다.");
        }

        validateDatasetDetailAccess(dataset, userId);

        DatasetViewLogDto viewLogDto = new DatasetViewLogDto();
        viewLogDto.setDatasetId(datasetId);
        viewLogDto.setUserId(userId);
        viewLogDto.setViewIp(viewIp);

        // 조회 로그 생성, 조회 수 증가
        recordDatasetView(viewLogDto);

        DatasetStatDto stats = datasetDownloadMapper.findDatasetStatById(datasetId);
        if (stats == null) {
            stats = new DatasetStatDto();
            stats.setDatasetId(datasetId);
            stats.setViewCount(0);
            stats.setDownloadCount(0);
        }

        DownloadDatasetFileDto sourceFile = datasetDownloadMapper.findSourceFileByDatasetId(datasetId);

        DatasetDownloadPageDto response = new DatasetDownloadPageDto();
        response.setDataset(dataset);
        response.setSourceFile(sourceFile);
        response.setStats(stats);
        response.setAvailableFormats(List.of("CSV", "GeoJSON", "SHP", "XLSX", "TIFF"));

        return response;
    }

    private void validateDatasetDetailAccess(DownloadDatasetDetailDto dataset, Integer userId) {
        if (Boolean.TRUE.equals(dataset.getIsPublic())) {
            return;
        }

        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비공개 데이터셋은 로그인 후 접근할 수 있습니다.");
        }

        String userOrganization = datasetDownloadMapper.findUserOrganization(userId);
        String datasetOwnerOrganization = datasetDownloadMapper.findDatasetOwnerOrganization(dataset.getDatasetId());

        if (!Objects.equals(normalize(userOrganization), normalize(datasetOwnerOrganization))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "동일 소속기관 사용자만 접근할 수 있습니다.");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    // 조회 로그 생성
    public void recordDatasetView(DatasetViewLogDto viewLogDto) {
        boolean duplicate = isDuplicateView(
                viewLogDto.getDatasetId(),
                viewLogDto.getUserId(),
                viewLogDto.getViewIp()
        );

        if (duplicate) {
            return;
        }

        datasetDownloadMapper.insertViewLog(viewLogDto);
        datasetDownloadMapper.increaseViewCount(viewLogDto.getDatasetId());
    }

    // 조회 로그, 조회수 중복 방지용 메서드
    private boolean isDuplicateView(Long datasetId, Integer userId, String viewIp) {
        LocalDateTime fromTime = LocalDateTime.now().minusMinutes(5);

        if (userId != null) {
            Integer recentCount = datasetDownloadMapper.countRecentViewByUser(datasetId, userId, fromTime);
            return recentCount != null && recentCount > 0;
        }

        Integer recentCount = datasetDownloadMapper.countRecentViewByIp(datasetId, viewIp, fromTime);
        return recentCount != null && recentCount > 0;
    }

    // 업로더의 소속기관과 사용자의 소속기관 비교
    public boolean hasSameOrganization(Integer userId, Long datasetId) {
        String userOrganization = datasetDownloadMapper.findUserOrganization(userId);
        String uploaderOrganization = datasetDownloadMapper.findDatasetOwnerOrganization(datasetId);

        boolean result = true;
        if (!userOrganization.equals(uploaderOrganization)) {
            result = false;
        }

        return result;
    }

    // 공간 데이터 미리보기 가져오기
    public String getDatasetPreviewGeoJson(Long datasetId, Integer userId) {
        DownloadDatasetDetailDto datasetDto = datasetDownloadMapper.findDatasetDetailById(datasetId);

        if (datasetDto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "데이터셋을 찾을 수 없습니다.");
        }

        validateDatasetDetailAccess(datasetDto, userId);

        String geoJson = datasetDownloadMapper.findDatasetPreviewGeoJson(datasetId);

        return geoJson != null ? geoJson : "{\"type\":\"FeatureCollection\",\"features\":[]}";
    }

    // 다운로드 파일 변환 과정
    public DownloadExportResultDto downloadDatasetByFormat(
            Long datasetId,
            String format,
            Integer userId,
            String downloadIp
    ) {
        DownloadDatasetDetailDto dataset = datasetDownloadMapper.findDatasetDetailById(datasetId);
        if (dataset == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "데이터셋을 찾을 수 없습니다.");
        }

        validateDatasetDetailAccess(dataset, userId);

        DownloadDatasetFileDto sourceFile = datasetDownloadMapper.findSourceFileByDatasetId(datasetId);
        if (sourceFile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "원본 파일 정보를 찾을 수 없습니다.");
        }

        String normalizedFormat = format == null ? "" : format.trim().toUpperCase(Locale.ROOT);
        String originalFormat = normalizeSourceFormat(sourceFile.getFileExtension());

        DownloadExportResultDto result;

        // 업로드된 원본 형식과 같으면 S3 원본 파일 다운로드
        if (normalizedFormat.equals(originalFormat)) {
            result = downloadOriginalFileFromS3(sourceFile);
        } else {
            // 원본 형식이 아니면 현재 구현된 변환 형식으로 다운로드
            switch (normalizedFormat) {
                case "CSV":
                    result = exportCsv(datasetId, dataset.getTitle());
                    break;
                case "GEOJSON":
                    result = exportGeoJson(datasetId, dataset.getTitle());
                    break;
                case "SHP":
                    throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "SHP 형식 변환 다운로드는 준비 중입니다.");
                case "XLSX":
                    result = exportXlsx(datasetId, dataset.getTitle());
                    break;
                default:
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 형식입니다.");
            }
        }

        recordDownloadSuccess(datasetId, sourceFile, userId, normalizedFormat, downloadIp);
        return result;
    }

    // 원본 파일 확장자를 버튼 형식과 비교하기 쉬운 형태로 맞춤
    private String normalizeSourceFormat(String fileExtension) {
        if (fileExtension == null || fileExtension.isBlank()) {
            return "";
        }

        String ext = fileExtension.trim().toUpperCase(Locale.ROOT);

        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }

        if ("JSON".equals(ext)) {
            return "GEOJSON";
        }

        // SHP는 zip 묶음으로 저장될 수 있으므로 SHP 버튼과 연결
        if ("ZIP".equals(ext)) {
            return "SHP";
        }

        // xls도 같은 엑셀 형식으로 취급
        if ("XLS".equals(ext)) {
            return "XLSX";
        }

        if ("TIF".equals(ext)) {
            return "TIFF";
        }

        return ext;
    }

    private DownloadExportResultDto downloadOriginalFileFromS3(DownloadDatasetFileDto sourceFile) {
        S3DownloadResult result = s3FileService.downloadFile(
                sourceFile.getFilePath(),
                sourceFile.getStoredFilename(),
                sourceFile.getOriginalFilename()
        );

        byte[] bytes = result.resource().getByteArray();

        return new DownloadExportResultDto(
                result.fileName(),
                result.contentType(),
                bytes
        );
    }

    private void recordDownloadSuccess(
            Long datasetId,
            DownloadDatasetFileDto sourceFile,
            Integer userId,
            String format,
            String downloadIp
    ) {
        DownloadLogDto logDto = new DownloadLogDto();
        logDto.setDatasetId(datasetId);
        logDto.setFileId(sourceFile.getFileId());
        logDto.setUserId(userId);
        logDto.setDownloadFormat(format);
        logDto.setDownloadStatus("SUCCESS");
        logDto.setErrorMessage(null);
        logDto.setDownloadIp(downloadIp);

        datasetDownloadMapper.insertDownloadLog(logDto);
        datasetDownloadMapper.increaseDownloadCount(datasetId);
    }

    private DownloadExportResultDto exportCsv(Long datasetId, String datasetTitle) {
        List<DatasetFeatureExportDto> features = datasetDownloadMapper.findDatasetFeaturesForExport(datasetId);

        if (features.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "변환할 공간 데이터가 없습니다.");
        }

        LinkedHashSet<String> propertyKeys = new LinkedHashSet<>();
        List<Map<String, Object>> propertyRows = new ArrayList<>();

        for (DatasetFeatureExportDto feature : features) {
            Map<String, Object> properties = parseProperties(feature.getPropertiesJson());
            propertyRows.add(properties);
            propertyKeys.addAll(properties.keySet());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("feature_id,feature_name,spatial_type,geometry_wkt");
        for (String key : propertyKeys) {
            sb.append(",").append(escapeCsv(key));
        }
        sb.append("\n");

        for (int i = 0; i < features.size(); i++) {
            DatasetFeatureExportDto feature = features.get(i);
            Map<String, Object> properties = propertyRows.get(i);

            sb.append(feature.getFeatureId()).append(",");
            sb.append(escapeCsv(feature.getFeatureName())).append(",");
            sb.append(escapeCsv(feature.getSpatialType())).append(",");
            sb.append(escapeCsv(feature.getGeometryWkt()));

            for (String key : propertyKeys) {
                Object value = properties.get(key);
                sb.append(",").append(escapeCsv(value == null ? "" : String.valueOf(value)));
            }
            sb.append("\n");
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        return new DownloadExportResultDto(
                datasetTitle + ".csv",
                "text/csv",
                bytes
        );
    }

    private Map<String, Object> parseProperties(String propertiesJson) {
        try {
            if (propertiesJson == null || propertiesJson.isBlank()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(propertiesJson, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "속성 데이터 파싱에 실패했습니다.", e);
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private DownloadExportResultDto exportGeoJson(Long datasetId, String datasetTitle) {
        String geoJson = datasetDownloadMapper.findDatasetExportGeoJson(datasetId);

        if (geoJson == null || geoJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "변환할 공간 데이터가 없습니다.");
        }

        return new DownloadExportResultDto(
                datasetTitle + ".geojson",
                "application/geo+json",
                geoJson.getBytes(StandardCharsets.UTF_8)
        );
    }

    private DownloadExportResultDto exportXlsx(Long datasetId, String datasetTitle) {
        List<DatasetFeatureExportDto> features = datasetDownloadMapper.findDatasetFeaturesForExport(datasetId);

        if (features.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "변환할 공간 데이터가 없습니다.");
        }

        LinkedHashSet<String> propertyKeys = new LinkedHashSet<>();
        List<Map<String, Object>> propertyRows = new ArrayList<>();

        for (DatasetFeatureExportDto feature : features) {
            Map<String, Object> properties = parseProperties(feature.getPropertiesJson());
            propertyRows.add(properties);
            propertyKeys.addAll(properties.keySet());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("dataset");

            Row headerRow = sheet.createRow(0);
            int headerIndex = 0;
            headerRow.createCell(headerIndex++).setCellValue("feature_id");
            headerRow.createCell(headerIndex++).setCellValue("feature_name");
            headerRow.createCell(headerIndex++).setCellValue("spatial_type");
            headerRow.createCell(headerIndex++).setCellValue("geometry_wkt");

            for (String key : propertyKeys) {
                headerRow.createCell(headerIndex++).setCellValue(key);
            }

            for (int i = 0; i < features.size(); i++) {
                DatasetFeatureExportDto feature = features.get(i);
                Map<String, Object> properties = propertyRows.get(i);

                Row row = sheet.createRow(i + 1);
                int cellIndex = 0;

                row.createCell(cellIndex++).setCellValue(feature.getFeatureId() == null ? "" : String.valueOf(feature.getFeatureId()));
                row.createCell(cellIndex++).setCellValue(feature.getFeatureName() == null ? "" : feature.getFeatureName());
                row.createCell(cellIndex++).setCellValue(feature.getSpatialType() == null ? "" : feature.getSpatialType());
                row.createCell(cellIndex++).setCellValue(feature.getGeometryWkt() == null ? "" : feature.getGeometryWkt());

                for (String key : propertyKeys) {
                    Object value = properties.get(key);
                    row.createCell(cellIndex++).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }

            workbook.write(outputStream);

            return new DownloadExportResultDto(
                    datasetTitle + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "XLSX 파일 생성에 실패했습니다.", e);
        }
    }
}
