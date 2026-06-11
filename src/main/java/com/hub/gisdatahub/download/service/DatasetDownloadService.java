package com.hub.gisdatahub.download.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import com.hub.gisdatahub.download.dto.DatasetFavoriteResponseDto;
import com.hub.gisdatahub.download.dto.DatasetStatDto;
import com.hub.gisdatahub.download.dto.DatasetViewLogDto;
import com.hub.gisdatahub.download.dto.DownloadAttributePreviewDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetDetailDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetFileDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetListItemDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetSearchOptionsDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetSearchResponseDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetSummaryDto;
import com.hub.gisdatahub.download.dto.DownloadExportResultDto;
import com.hub.gisdatahub.download.dto.DownloadFormatOptionDto;
import com.hub.gisdatahub.download.dto.DownloadLogDto;
import com.hub.gisdatahub.download.mapper.DatasetDownloadMapper;
import com.hub.gisdatahub.s3.dto.S3DownloadResult;
import com.hub.gisdatahub.s3.service.S3FileService;
import com.hub.gisdatahub.user.mapper.UserMapper;

@Service
public class DatasetDownloadService {

    private static final List<String> DOWNLOAD_FORMATS = List.of("CSV", "GeoJSON", "SHP", "XLSX", "TIFF");
    private static final List<String> CONVERTIBLE_FORMATS = List.of("CSV", "GEOJSON", "SHP", "XLSX");
    private static final Charset DBF_CHARSET = StandardCharsets.UTF_8;

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

    public DownloadDatasetSearchResponseDto getDownloadDatasetMainPage(
            String keyword,
            String provider,
            String fileFormat,
            Integer categoryId,
            LocalDate startDate,
            LocalDate endDate,
            Boolean downloadedToday,
            Integer page,
            Integer size,
            String sort,
            Integer userId
    ) {
        String normalizedKeyword = normalizeFilter(keyword);
        String normalizedProvider = normalizeFilter(provider);
        String normalizedFileFormat = normalizeFilter(fileFormat);
        String normalizedSort = normalizeSort(sort);
        boolean todayDownloadOnly = Boolean.TRUE.equals(downloadedToday);
        int safeSize = clamp(size == null ? 10 : size, 1, 50);
        int safePage = Math.max(page == null ? 1 : page, 1);

        Integer totalCountValue = todayDownloadOnly
                ? datasetDownloadMapper.countTodayDownloadedApprovedDatasets(
                        normalizedKeyword,
                        normalizedProvider,
                        normalizedFileFormat,
                        categoryId,
                        startDate,
                        endDate
                )
                : datasetDownloadMapper.countApprovedDownloadDatasets(
                        normalizedKeyword,
                        normalizedProvider,
                        normalizedFileFormat,
                        categoryId,
                        startDate,
                        endDate
                );
        int totalCount = totalCountValue == null ? 0 : totalCountValue;
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / safeSize);

        if (totalPages > 0 && safePage > totalPages) {
            safePage = totalPages;
        }

        int offset = (safePage - 1) * safeSize;
        List<DownloadDatasetListItemDto> datasetList = totalCount == 0
                ? List.of()
                : todayDownloadOnly
                        ? datasetDownloadMapper.findTodayDownloadedApprovedDatasetPage(
                                normalizedKeyword,
                                normalizedProvider,
                                normalizedFileFormat,
                                categoryId,
                                startDate,
                                endDate,
                                safeSize,
                                offset,
                                normalizedSort,
                                userId
                        )
                        : datasetDownloadMapper.findApprovedDownloadDatasetPage(
                                normalizedKeyword,
                                normalizedProvider,
                                normalizedFileFormat,
                                categoryId,
                                startDate,
                                endDate,
                                safeSize,
                                offset,
                                normalizedSort,
                                userId
                        );

        DownloadDatasetSearchResponseDto response = new DownloadDatasetSearchResponseDto();
        response.setDatasetList(datasetList);
        response.setSummary(buildMainPageSummary());
        response.setOptions(buildSearchOptions());
        response.setPage(safePage);
        response.setSize(safeSize);
        response.setTotalCount(totalCount);
        response.setTotalPages(totalPages);

        return response;
    }

    private DownloadDatasetSummaryDto buildMainPageSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();

        Integer totalDatasetCount = datasetDownloadMapper.countApprovedDownloadDatasets(
                null,
                null,
                null,
                null,
                null,
                null
        );
        Integer todayDownloadCount = datasetDownloadMapper.countDownloadLogsBetween(todayStart, tomorrowStart);
        Integer yesterdayDownloadCount = datasetDownloadMapper.countDownloadLogsBetween(yesterdayStart, todayStart);
        List<String> supportedFormats = datasetDownloadMapper.findDownloadSearchFileFormats();

        DownloadDatasetSummaryDto summary = new DownloadDatasetSummaryDto();
        summary.setTotalDatasetCount(totalDatasetCount == null ? 0 : totalDatasetCount);
        summary.setTodayDownloadCount(todayDownloadCount == null ? 0 : todayDownloadCount);
        summary.setYesterdayDownloadCount(yesterdayDownloadCount == null ? 0 : yesterdayDownloadCount);
        summary.setDownloadChangeRate(calculateChangeRate(summary.getTodayDownloadCount(), summary.getYesterdayDownloadCount()));
        summary.setSupportedFormats(supportedFormats);
        summary.setSupportedFormatCount(supportedFormats == null ? 0 : supportedFormats.size());
        summary.setPopularDataset(datasetDownloadMapper.findPopularApprovedDataset());

        return summary;
    }

    private DownloadDatasetSearchOptionsDto buildSearchOptions() {
        DownloadDatasetSearchOptionsDto options = new DownloadDatasetSearchOptionsDto();
        options.setProviders(datasetDownloadMapper.findDownloadSearchProviders());
        options.setFileFormats(datasetDownloadMapper.findDownloadSearchFileFormats());
        options.setCategories(datasetDownloadMapper.findDownloadSearchCategories());
        return options;
    }

    private double calculateChangeRate(int todayCount, int yesterdayCount) {
        if (yesterdayCount == 0) {
            return todayCount > 0 ? 100.0 : 0.0;
        }

        double rate = ((double) (todayCount - yesterdayCount) / yesterdayCount) * 100;
        return Math.round(rate * 10.0) / 10.0;
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSort(String sort) {
        String normalized = normalizeFilter(sort);
        if (normalized == null) {
            return "default";
        }

        return switch (normalized) {
            case "viewCount", "downloadCount", "updatedAt", "title" -> normalized;
            default -> "default";
        };
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
        List<DownloadFormatOptionDto> downloadFormats = buildDownloadFormatOptions(datasetId, dataset, sourceFile);
        List<String> availableFormats = new ArrayList<>();
        for (DownloadFormatOptionDto option : downloadFormats) {
            if (Boolean.TRUE.equals(option.getAvailable())) {
                availableFormats.add(option.getFormat());
            }
        }
        response.setAvailableFormats(availableFormats);
        response.setDownloadFormats(downloadFormats);
        response.setAttributePreview(buildAttributePreview(datasetId));
        response.setRelatedDatasets(datasetDownloadMapper.findRelatedApprovedDatasets(datasetId));
        response.setFavorite(userId != null && isDatasetFavorite(datasetId, userId));

        return response;
    }

    public DatasetFavoriteResponseDto toggleDatasetFavorite(Long datasetId, Integer userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요한 기능입니다.");
        }

        DownloadDatasetDetailDto dataset = datasetDownloadMapper.findDatasetDetailById(datasetId);

        if (dataset == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "데이터셋을 찾을 수 없습니다.");
        }

        validateDatasetDetailAccess(dataset, userId);

        boolean favorite = isDatasetFavorite(datasetId, userId);
        boolean nextFavorite = !favorite;

        if (favorite) {
            datasetDownloadMapper.deleteDatasetFavorite(datasetId, userId);
        } else {
            datasetDownloadMapper.insertDatasetFavorite(datasetId, userId);
        }

        DatasetFavoriteResponseDto response = new DatasetFavoriteResponseDto();
        response.setFavorite(nextFavorite);
        return response;
    }

    private boolean isDatasetFavorite(Long datasetId, Integer userId) {
        Integer count = datasetDownloadMapper.countDatasetFavorite(datasetId, userId);
        return count != null && count > 0;
    }

    private List<DownloadFormatOptionDto> buildDownloadFormatOptions(
            Long datasetId,
            DownloadDatasetDetailDto dataset,
            DownloadDatasetFileDto sourceFile
    ) {
        List<DownloadFormatOptionDto> options = new ArrayList<>();
        String originalFormat = sourceFile == null ? "" : normalizeSourceFormat(sourceFile.getFileExtension());

        for (String format : DOWNLOAD_FORMATS) {
            String normalizedFormat = normalizeDownloadFormat(format);
            DownloadFormatOptionDto option = new DownloadFormatOptionDto();
            option.setFormat(format);
            option.setAvailable(false);
            option.setOriginal(false);

            if (sourceFile == null) {
                option.setReason("원본 파일 정보 없음");
            } else if (normalizedFormat.equals(originalFormat)) {
                option.setAvailable(true);
                option.setOriginal(true);
                option.setFileSize(sourceFile.getFileSize());
                option.setReason("원본 파일");
            } else if (CONVERTIBLE_FORMATS.contains(normalizedFormat)) {
                Long convertedFileSize = calculateConvertedFileSize(datasetId, dataset.getTitle(), normalizedFormat);
                if (convertedFileSize == null) {
                    option.setReason("변환할 데이터 없음");
                } else {
                    option.setAvailable(true);
                    option.setFileSize(convertedFileSize);
                    option.setReason("변환 가능");
                }
            } else {
                option.setReason("변환 미지원");
            }

            options.add(option);
        }

        return options;
    }

    private Long calculateConvertedFileSize(Long datasetId, String datasetTitle, String normalizedFormat) {
        try {
            DownloadExportResultDto result = switch (normalizedFormat) {
                case "CSV" -> exportCsv(datasetId, datasetTitle);
                case "GEOJSON" -> exportGeoJson(datasetId, datasetTitle);
                case "SHP" -> exportShp(datasetId, datasetTitle);
                case "XLSX" -> exportXlsx(datasetId, datasetTitle);
                default -> null;
            };

            if (result == null || result.getBytes() == null) {
                return null;
            }

            return (long) result.getBytes().length;
        } catch (ResponseStatusException e) {
            return null;
        }
    }

    private DownloadAttributePreviewDto buildAttributePreview(Long datasetId) {
        List<DatasetFeatureExportDto> features = datasetDownloadMapper.findDatasetAttributePreviewRows(datasetId);
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        columns.add("feature_id");
        columns.add("feature_name");
        columns.add("spatial_type");

        for (DatasetFeatureExportDto feature : features) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("feature_id", feature.getFeatureId());
            row.put("feature_name", feature.getFeatureName());
            row.put("spatial_type", feature.getSpatialType());

            Map<String, Object> properties = parseProperties(feature.getPropertiesJson());
            row.putAll(properties);
            columns.addAll(properties.keySet());
            rows.add(row);
        }

        List<String> columnList = new ArrayList<>(columns);
        for (Map<String, Object> row : rows) {
            for (String column : columnList) {
                row.putIfAbsent(column, null);
            }
        }

        DownloadAttributePreviewDto preview = new DownloadAttributePreviewDto();
        preview.setColumns(columnList);
        preview.setRows(rows);
        return preview;
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

        String normalizedFormat = normalizeDownloadFormat(format);
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
                    result = exportShp(datasetId, dataset.getTitle());
                    break;
                case "XLSX":
                    result = exportXlsx(datasetId, dataset.getTitle());
                    break;
                default:
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 형식입니다.");
            }
        }

        result.setFileName(buildDownloadFileName(dataset.getTitle(), normalizedFormat));

        Long downloadFileSize = result.getBytes() == null ? null : (long) result.getBytes().length;
        recordDownloadSuccess(datasetId, sourceFile, userId, normalizedFormat, downloadFileSize, downloadIp);
        return result;
    }

    private String buildDownloadFileName(String datasetTitle, String normalizedFormat) {
        String title = datasetTitle == null || datasetTitle.isBlank() ? "dataset" : datasetTitle.trim();
        String format = normalizedFormat == null ? "" : normalizedFormat;
        String extension = switch (format) {
            case "GEOJSON" -> "geojson";
            case "SHP" -> "zip";
            case "XLSX" -> "xlsx";
            case "CSV" -> "csv";
            case "TIFF" -> "tiff";
            default -> format.isBlank() ? "download" : format.toLowerCase(Locale.ROOT);
        };

        return title + "." + extension;
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

    private String normalizeDownloadFormat(String format) {
        if (format == null || format.isBlank()) {
            return "";
        }

        String normalizedFormat = format.trim().toUpperCase(Locale.ROOT);
        if ("JSON".equals(normalizedFormat)) {
            return "GEOJSON";
        }

        return normalizedFormat;
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
            Long downloadFileSize,
            String downloadIp
    ) {
        DownloadLogDto logDto = new DownloadLogDto();
        logDto.setDatasetId(datasetId);
        logDto.setFileId(sourceFile.getFileId());
        logDto.setUserId(userId);
        logDto.setDownloadFormat(format);
        logDto.setDownloadFileSize(downloadFileSize);
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
    private DownloadExportResultDto exportShp(Long datasetId, String datasetTitle) {
        List<DatasetFeatureExportDto> sourceFeatures = datasetDownloadMapper.findDatasetFeaturesForExport(datasetId);

        if (sourceFeatures.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SHP로 변환할 공간 데이터가 없습니다.");
        }

        Map<ShpShapeGroup, List<ShpFeature>> groupedFeatures = new LinkedHashMap<>();
        for (ShpShapeGroup group : ShpShapeGroup.values()) {
            groupedFeatures.put(group, new ArrayList<>());
        }

        for (DatasetFeatureExportDto feature : sourceFeatures) {
            for (ShpFeature shpFeature : parseShpFeatures(feature)) {
                groupedFeatures.get(shpFeature.group).add(shpFeature);
            }
        }

        int availableGroupCount = 0;
        for (List<ShpFeature> features : groupedFeatures.values()) {
            if (!features.isEmpty()) {
                availableGroupCount++;
            }
        }

        if (availableGroupCount == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SHP로 변환할 수 있는 공간 형식이 없습니다.");
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {

            String baseName = sanitizeZipEntryName(datasetTitle);
            for (Map.Entry<ShpShapeGroup, List<ShpFeature>> entry : groupedFeatures.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }

                ShpShapeGroup group = entry.getKey();
                ShpFileSet fileSet = buildShpFileSet(entry.getValue(), group);
                String entryBaseName = availableGroupCount == 1 ? baseName : baseName + "_" + group.fileSuffix;

                addZipEntry(zipOutputStream, entryBaseName + ".shp", fileSet.shp);
                addZipEntry(zipOutputStream, entryBaseName + ".shx", fileSet.shx);
                addZipEntry(zipOutputStream, entryBaseName + ".dbf", fileSet.dbf);
                addZipEntry(zipOutputStream, entryBaseName + ".prj", fileSet.prj);
                addZipEntry(zipOutputStream, entryBaseName + ".cpg", fileSet.cpg);
            }

            zipOutputStream.finish();
            byte[] bytes = outputStream.toByteArray();

            return new DownloadExportResultDto(
                    datasetTitle + ".zip",
                    "application/zip",
                    bytes
            );
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SHP 파일 생성에 실패했습니다.", e);
        }
    }

    private List<ShpFeature> parseShpFeatures(DatasetFeatureExportDto feature) {
        String wkt = feature.getGeometryWkt();
        if (wkt == null || wkt.isBlank()) {
            return List.of();
        }

        String normalizedWkt = wkt.trim();
        int sridSeparator = normalizedWkt.indexOf(';');
        if (sridSeparator >= 0) {
            normalizedWkt = normalizedWkt.substring(sridSeparator + 1).trim();
        }

        int bodyStartIndex = normalizedWkt.indexOf('(');
        if (bodyStartIndex < 0) {
            return List.of();
        }

        String type = normalizeWktType(normalizedWkt.substring(0, bodyStartIndex));
        String body = normalizedWkt.substring(bodyStartIndex);

        return switch (type) {
            case "POINT" -> parsePointFeature(feature, body);
            case "MULTIPOINT" -> parseMultiPointFeature(feature, body);
            case "LINESTRING" -> parseLineFeature(feature, body);
            case "MULTILINESTRING" -> parseMultiLineFeature(feature, body);
            case "POLYGON" -> parsePolygonFeature(feature, body);
            case "MULTIPOLYGON" -> parseMultiPolygonFeature(feature, body);
            case "GEOMETRYCOLLECTION" -> parseGeometryCollectionFeature(feature, body);
            default -> List.of();
        };
    }

    private List<ShpFeature> parsePointFeature(DatasetFeatureExportDto feature, String body) {
        ShpPoint point = parsePoint(stripOuterParentheses(body));
        if (point == null) {
            return List.of();
        }
        return List.of(toShpFeature(feature, ShpShapeGroup.POINT, List.of(List.of(point))));
    }

    private List<ShpFeature> parseMultiPointFeature(DatasetFeatureExportDto feature, String body) {
        List<ShpFeature> result = new ArrayList<>();
        for (String pointText : splitTopLevel(stripOuterParentheses(body))) {
            ShpPoint point = parsePoint(stripOuterParentheses(pointText));
            if (point != null) {
                result.add(toShpFeature(feature, ShpShapeGroup.POINT, List.of(List.of(point))));
            }
        }
        return result;
    }

    private List<ShpFeature> parseLineFeature(DatasetFeatureExportDto feature, String body) {
        List<ShpPoint> points = parsePointList(stripOuterParentheses(body));
        if (points.size() < 2) {
            return List.of();
        }
        return List.of(toShpFeature(feature, ShpShapeGroup.POLYLINE, List.of(points)));
    }

    private List<ShpFeature> parseMultiLineFeature(DatasetFeatureExportDto feature, String body) {
        List<List<ShpPoint>> parts = new ArrayList<>();
        for (String lineText : splitTopLevel(stripSingleOuterParentheses(body))) {
            List<ShpPoint> points = parsePointList(stripOuterParentheses(lineText));
            if (points.size() >= 2) {
                parts.add(points);
            }
        }
        if (parts.isEmpty()) {
            return List.of();
        }
        return List.of(toShpFeature(feature, ShpShapeGroup.POLYLINE, parts));
    }

    private List<ShpFeature> parsePolygonFeature(DatasetFeatureExportDto feature, String body) {
        List<List<ShpPoint>> rings = parsePolygonRings(body);
        if (rings.isEmpty()) {
            return List.of();
        }
        return List.of(toShpFeature(feature, ShpShapeGroup.POLYGON, rings));
    }

    private List<ShpFeature> parseMultiPolygonFeature(DatasetFeatureExportDto feature, String body) {
        List<List<ShpPoint>> parts = new ArrayList<>();
        for (String polygonText : splitTopLevel(stripSingleOuterParentheses(body))) {
            parts.addAll(parsePolygonRings(polygonText));
        }
        if (parts.isEmpty()) {
            return List.of();
        }
        return List.of(toShpFeature(feature, ShpShapeGroup.POLYGON, parts));
    }

    private List<ShpFeature> parseGeometryCollectionFeature(DatasetFeatureExportDto feature, String body) {
        List<ShpFeature> result = new ArrayList<>();
        for (String geometryText : splitTopLevel(stripSingleOuterParentheses(body))) {
            DatasetFeatureExportDto copiedFeature = new DatasetFeatureExportDto();
            copiedFeature.setFeatureId(feature.getFeatureId());
            copiedFeature.setFeatureName(feature.getFeatureName());
            copiedFeature.setSpatialType(feature.getSpatialType());
            copiedFeature.setPropertiesJson(feature.getPropertiesJson());
            copiedFeature.setGeometryWkt(geometryText);
            result.addAll(parseShpFeatures(copiedFeature));
        }
        return result;
    }

    private List<List<ShpPoint>> parsePolygonRings(String body) {
        List<List<ShpPoint>> rings = new ArrayList<>();
        for (String ringText : splitTopLevel(stripSingleOuterParentheses(body))) {
            List<ShpPoint> points = parsePointList(stripOuterParentheses(ringText));
            if (points.size() >= 3) {
                rings.add(closeRing(points));
            }
        }
        return rings;
    }

    private List<ShpPoint> parsePointList(String pointsText) {
        List<ShpPoint> points = new ArrayList<>();
        for (String pointText : splitTopLevel(pointsText)) {
            ShpPoint point = parsePoint(pointText);
            if (point != null) {
                points.add(point);
            }
        }
        return points;
    }

    private ShpPoint parsePoint(String pointText) {
        if (pointText == null || pointText.isBlank()) {
            return null;
        }

        String normalized = stripOuterParentheses(pointText)
                .replace(",", " ")
                .trim()
                .replaceAll("\\s+", " ");
        String[] values = normalized.split(" ");
        if (values.length < 2) {
            return null;
        }

        try {
            return new ShpPoint(Double.parseDouble(values[0]), Double.parseDouble(values[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<ShpPoint> closeRing(List<ShpPoint> points) {
        if (points.isEmpty()) {
            return points;
        }

        List<ShpPoint> closed = new ArrayList<>(points);
        ShpPoint first = closed.get(0);
        ShpPoint last = closed.get(closed.size() - 1);
        if (Double.compare(first.x, last.x) != 0 || Double.compare(first.y, last.y) != 0) {
            closed.add(new ShpPoint(first.x, first.y));
        }
        return closed;
    }

    private String normalizeWktType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (normalized.endsWith(" ZM")) {
            return normalized.substring(0, normalized.length() - 3).trim();
        }
        if (normalized.endsWith(" Z") || normalized.endsWith(" M")) {
            return normalized.substring(0, normalized.length() - 2).trim();
        }
        return normalized;
    }

    private String stripOuterParentheses(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        while (trimmed.startsWith("(") && trimmed.endsWith(")") && isOuterParenthesesPair(trimmed)) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String stripSingleOuterParentheses(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")") && isOuterParenthesesPair(trimmed)) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private boolean isOuterParenthesesPair(String value) {
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0 && i < value.length() - 1) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    private List<String> splitTopLevel(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        int depth = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                result.add(value.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(value.substring(start).trim());
        return result;
    }

    private ShpFeature toShpFeature(
            DatasetFeatureExportDto feature,
            ShpShapeGroup group,
            List<List<ShpPoint>> parts
    ) {
        String name = feature.getFeatureName();
        if (name == null || name.isBlank()) {
            name = feature.getFeatureId() == null ? "" : String.valueOf(feature.getFeatureId());
        }

        String spatialType = feature.getSpatialType();
        if (spatialType == null || spatialType.isBlank()) {
            spatialType = group.label;
        }

        return new ShpFeature(feature.getFeatureId(), name, spatialType, group, parts);
    }

    private ShpFileSet buildShpFileSet(List<ShpFeature> features, ShpShapeGroup group) throws IOException {
        List<ShpRecord> records = new ArrayList<>();
        ShpBounds bounds = new ShpBounds();

        for (ShpFeature feature : features) {
            byte[] content = switch (group) {
                case POINT -> buildPointRecordContent(feature);
                case POLYLINE, POLYGON -> buildPartRecordContent(feature);
            };

            if (content.length == 0) {
                continue;
            }

            records.add(new ShpRecord(feature, content));
            bounds.include(feature.points());
        }

        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SHP로 변환할 수 있는 공간 객체가 없습니다.");
        }

        return new ShpFileSet(
                buildShp(records, group, bounds),
                buildShx(records, group, bounds),
                buildDbf(records),
                buildPrj(),
                "UTF-8".getBytes(StandardCharsets.UTF_8)
        );
    }

    private byte[] buildPointRecordContent(ShpFeature feature) throws IOException {
        if (feature.parts.isEmpty() || feature.parts.get(0).isEmpty()) {
            return new byte[0];
        }

        ShpPoint point = feature.parts.get(0).get(0);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            writeIntLE(dataOutputStream, ShpShapeGroup.POINT.shapeType);
            writeDoubleLE(dataOutputStream, point.x);
            writeDoubleLE(dataOutputStream, point.y);
            return outputStream.toByteArray();
        }
    }

    private byte[] buildPartRecordContent(ShpFeature feature) throws IOException {
        List<List<ShpPoint>> parts = new ArrayList<>();
        int pointCount = 0;
        int minimumPointCount = feature.group == ShpShapeGroup.POLYLINE ? 2 : 4;

        for (List<ShpPoint> part : feature.parts) {
            if (part.size() >= minimumPointCount) {
                parts.add(part);
                pointCount += part.size();
            }
        }

        if (parts.isEmpty()) {
            return new byte[0];
        }

        ShpBounds bounds = new ShpBounds();
        bounds.include(parts);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            writeIntLE(dataOutputStream, feature.group.shapeType);
            writeBoundsLE(dataOutputStream, bounds);
            writeIntLE(dataOutputStream, parts.size());
            writeIntLE(dataOutputStream, pointCount);

            int partOffset = 0;
            for (List<ShpPoint> part : parts) {
                writeIntLE(dataOutputStream, partOffset);
                partOffset += part.size();
            }

            for (List<ShpPoint> part : parts) {
                for (ShpPoint point : part) {
                    writeDoubleLE(dataOutputStream, point.x);
                    writeDoubleLE(dataOutputStream, point.y);
                }
            }

            return outputStream.toByteArray();
        }
    }

    private byte[] buildShp(List<ShpRecord> records, ShpShapeGroup group, ShpBounds bounds) throws IOException {
        int fileLengthBytes = 100;
        for (ShpRecord record : records) {
            fileLengthBytes += 8 + record.content.length;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            writeShpHeader(dataOutputStream, group.shapeType, fileLengthBytes / 2, bounds);

            for (int i = 0; i < records.size(); i++) {
                ShpRecord record = records.get(i);
                writeIntBE(dataOutputStream, i + 1);
                writeIntBE(dataOutputStream, record.content.length / 2);
                dataOutputStream.write(record.content);
            }

            return outputStream.toByteArray();
        }
    }

    private byte[] buildShx(List<ShpRecord> records, ShpShapeGroup group, ShpBounds bounds) throws IOException {
        int fileLengthBytes = 100 + (records.size() * 8);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            writeShpHeader(dataOutputStream, group.shapeType, fileLengthBytes / 2, bounds);

            int offsetWords = 50;
            for (ShpRecord record : records) {
                writeIntBE(dataOutputStream, offsetWords);
                writeIntBE(dataOutputStream, record.content.length / 2);
                offsetWords += (8 + record.content.length) / 2;
            }

            return outputStream.toByteArray();
        }
    }

    private byte[] buildDbf(List<ShpRecord> records) throws IOException {
        int headerLength = 32 + (3 * 32) + 1;
        int recordLength = 1 + 18 + 80 + 16;
        LocalDate now = LocalDate.now();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            dataOutputStream.writeByte(0x03);
            dataOutputStream.writeByte(now.getYear() - 1900);
            dataOutputStream.writeByte(now.getMonthValue());
            dataOutputStream.writeByte(now.getDayOfMonth());
            writeIntLE(dataOutputStream, records.size());
            writeShortLE(dataOutputStream, headerLength);
            writeShortLE(dataOutputStream, recordLength);
            for (int i = 0; i < 20; i++) {
                dataOutputStream.writeByte(0);
            }

            writeDbfFieldDescriptor(dataOutputStream, "FID", 'N', 18, 0);
            writeDbfFieldDescriptor(dataOutputStream, "NAME", 'C', 80, 0);
            writeDbfFieldDescriptor(dataOutputStream, "TYPE", 'C', 16, 0);
            dataOutputStream.writeByte(0x0D);

            for (ShpRecord record : records) {
                dataOutputStream.writeByte(' ');
                writeDbfNumber(dataOutputStream, record.feature.featureId, 18);
                writeDbfText(dataOutputStream, record.feature.name, 80);
                writeDbfText(dataOutputStream, record.feature.spatialType, 16);
            }
            dataOutputStream.writeByte(0x1A);

            return outputStream.toByteArray();
        }
    }

    private byte[] buildPrj() {
        String wgs84 = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\","
                + "SPHEROID[\"WGS 84\",6378137,298.257223563]],"
                + "PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]";
        return wgs84.getBytes(StandardCharsets.UTF_8);
    }

    private void writeShpHeader(
            DataOutputStream outputStream,
            int shapeType,
            int fileLengthWords,
            ShpBounds bounds
    ) throws IOException {
        writeIntBE(outputStream, 9994);
        for (int i = 0; i < 5; i++) {
            writeIntBE(outputStream, 0);
        }
        writeIntBE(outputStream, fileLengthWords);
        writeIntLE(outputStream, 1000);
        writeIntLE(outputStream, shapeType);
        writeBoundsLE(outputStream, bounds);
        for (int i = 0; i < 4; i++) {
            writeDoubleLE(outputStream, 0.0);
        }
    }

    private void writeBoundsLE(DataOutputStream outputStream, ShpBounds bounds) throws IOException {
        writeDoubleLE(outputStream, bounds.minX());
        writeDoubleLE(outputStream, bounds.minY());
        writeDoubleLE(outputStream, bounds.maxX());
        writeDoubleLE(outputStream, bounds.maxY());
    }

    private void writeDbfFieldDescriptor(
            DataOutputStream outputStream,
            String name,
            char type,
            int length,
            int decimalCount
    ) throws IOException {
        byte[] nameBytes = new byte[11];
        byte[] rawNameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(rawNameBytes, 0, nameBytes, 0, Math.min(rawNameBytes.length, 10));
        outputStream.write(nameBytes);
        outputStream.writeByte(type);
        writeIntLE(outputStream, 0);
        outputStream.writeByte(length);
        outputStream.writeByte(decimalCount);
        for (int i = 0; i < 14; i++) {
            outputStream.writeByte(0);
        }
    }

    private void writeDbfText(DataOutputStream outputStream, String value, int length) throws IOException {
        byte[] bytes = fitDbfText(value == null ? "" : value, length);
        outputStream.write(bytes);
        for (int i = bytes.length; i < length; i++) {
            outputStream.writeByte(' ');
        }
    }

    private void writeDbfNumber(DataOutputStream outputStream, Long value, int length) throws IOException {
        String number = value == null ? "" : String.valueOf(value);
        if (number.length() > length) {
            number = number.substring(number.length() - length);
        }

        for (int i = number.length(); i < length; i++) {
            outputStream.writeByte(' ');
        }
        outputStream.write(number.getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] fitDbfText(String value, int length) {
        String text = value;
        while (!text.isEmpty() && text.getBytes(DBF_CHARSET).length > length) {
            text = text.substring(0, text.length() - 1);
        }
        return text.getBytes(DBF_CHARSET);
    }

    private void addZipEntry(ZipOutputStream zipOutputStream, String entryName, byte[] bytes) throws IOException {
        ZipEntry zipEntry = new ZipEntry(entryName);
        zipOutputStream.putNextEntry(zipEntry);
        zipOutputStream.write(bytes);
        zipOutputStream.closeEntry();
    }

    private String sanitizeZipEntryName(String value) {
        String name = value == null || value.isBlank() ? "dataset" : value.trim();
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void writeIntBE(DataOutputStream outputStream, int value) throws IOException {
        outputStream.writeInt(value);
    }

    private void writeIntLE(DataOutputStream outputStream, int value) throws IOException {
        outputStream.writeInt(Integer.reverseBytes(value));
    }

    private void writeShortLE(DataOutputStream outputStream, int value) throws IOException {
        outputStream.writeByte(value & 0xFF);
        outputStream.writeByte((value >>> 8) & 0xFF);
    }

    private void writeDoubleLE(DataOutputStream outputStream, double value) throws IOException {
        outputStream.writeLong(Long.reverseBytes(Double.doubleToLongBits(value)));
    }

    private enum ShpShapeGroup {
        POINT(1, "point", "POINT"),
        POLYLINE(3, "line", "LINE"),
        POLYGON(5, "polygon", "POLYGON");

        private final int shapeType;
        private final String fileSuffix;
        private final String label;

        ShpShapeGroup(int shapeType, String fileSuffix, String label) {
            this.shapeType = shapeType;
            this.fileSuffix = fileSuffix;
            this.label = label;
        }
    }

    private static class ShpFeature {
        private final Long featureId;
        private final String name;
        private final String spatialType;
        private final ShpShapeGroup group;
        private final List<List<ShpPoint>> parts;

        private ShpFeature(
                Long featureId,
                String name,
                String spatialType,
                ShpShapeGroup group,
                List<List<ShpPoint>> parts
        ) {
            this.featureId = featureId;
            this.name = name;
            this.spatialType = spatialType;
            this.group = group;
            this.parts = parts;
        }

        private List<List<ShpPoint>> points() {
            return parts;
        }
    }

    private static class ShpPoint {
        private final double x;
        private final double y;

        private ShpPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class ShpRecord {
        private final ShpFeature feature;
        private final byte[] content;

        private ShpRecord(ShpFeature feature, byte[] content) {
            this.feature = feature;
            this.content = content;
        }
    }

    private static class ShpFileSet {
        private final byte[] shp;
        private final byte[] shx;
        private final byte[] dbf;
        private final byte[] prj;
        private final byte[] cpg;

        private ShpFileSet(byte[] shp, byte[] shx, byte[] dbf, byte[] prj, byte[] cpg) {
            this.shp = shp;
            this.shx = shx;
            this.dbf = dbf;
            this.prj = prj;
            this.cpg = cpg;
        }
    }

    private static class ShpBounds {
        private boolean empty = true;
        private double minX;
        private double minY;
        private double maxX;
        private double maxY;

        private void include(List<List<ShpPoint>> parts) {
            for (List<ShpPoint> part : parts) {
                for (ShpPoint point : part) {
                    include(point);
                }
            }
        }

        private void include(ShpPoint point) {
            if (point == null) {
                return;
            }

            if (empty) {
                minX = point.x;
                maxX = point.x;
                minY = point.y;
                maxY = point.y;
                empty = false;
                return;
            }

            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }

        private double minX() {
            return empty ? 0.0 : minX;
        }

        private double minY() {
            return empty ? 0.0 : minY;
        }

        private double maxX() {
            return empty ? 0.0 : maxX;
        }

        private double maxY() {
            return empty ? 0.0 : maxY;
        }
    }
}
