package com.hub.gisdatahub.download.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
import com.hub.gisdatahub.download.mapper.DatasetDownloadMapper;
import com.hub.gisdatahub.user.mapper.UserMapper;

@Service
public class DatasetDownloadService {

    private final DatasetDownloadMapper datasetDownloadMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatasetDownloadService(DatasetDownloadMapper datasetDownloadMapper, UserMapper userMapper) {
        this.datasetDownloadMapper = datasetDownloadMapper;
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
        // response.setAvailableFormats(List.of("CSV", "GeoJSON", "SHP", "GeoTIFF","KML"));
        response.setAvailableFormats(List.of("CSV", "GeoJSON", "SHP", "KML"));

        return response;
    }

    private void validateDatasetDetailAccess(DownloadDatasetDetailDto dataset, Integer userId) {
        // 공개 데이터셋일 경우 
        if (Boolean.TRUE.equals(dataset.getIsPublic())) {
            return;  // 그냥 통과
        }
        //  로그인을 하지 않았을경우
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비공개 데이터셋은 로그인 후 접근할 수 있습니다.");
        }

        // 소속기관 갖고오기
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
    public void recordDatasetView(DatasetViewLogDto viewLogDto){
        boolean duplicate = isDuplicateView(
            viewLogDto.getDatasetId(),
            viewLogDto.getUserId(),
            viewLogDto.getViewIp()
        );

        //  이미 최근 조회한 기록이 있다면 로그 추가, 조회수 증가 하지 않음
        if (duplicate) {
            return;
        }

        datasetDownloadMapper.insertViewLog(viewLogDto);
        datasetDownloadMapper.increaseViewCount(viewLogDto.getDatasetId());
    }

    // 조회 로그, 조회수 중복 방지용 메서드
    private boolean isDuplicateView(Long datasetId, Integer userId, String viewIp){
        LocalDateTime fromTime = LocalDateTime.now().minusMinutes(5); // 중복 방지 범위 시간 지정 현재는 5분

        if (userId != null) {
            Integer recentCount = datasetDownloadMapper.countRecentViewByUser(datasetId, userId, fromTime);
            return recentCount != null && recentCount > 0;
        }
        
        Integer recentCount = datasetDownloadMapper.countRecentViewByIp(datasetId, viewIp, fromTime);
        return recentCount != null && recentCount > 0;
        
    }

    // 업로드자의 소속기관, 사용자 소속기관 비교
    public boolean hasSameOrganization(Integer userId, Long datasetId){
        String userOrganization = datasetDownloadMapper.findUserOrganization(userId);
        String uploderOrganization = datasetDownloadMapper.findDatasetOwnerOrganization(datasetId);

        boolean result = true;
        if(!userOrganization.equals(uploderOrganization)) result = false;

        return result;
    }

    //  공간 데이터 갖고오기
    public String getDatasetPreviewGeoJson(Long datasetId, Integer userId){
        DownloadDatasetDetailDto datasetDto = datasetDownloadMapper.findDatasetDetailById(datasetId);

        if(datasetDto == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "데이터셋을 찾을 수 없습니다.");
        }

        validateDatasetDetailAccess(datasetDto, userId);

        String geoJson = datasetDownloadMapper.findDatasetPreviewGeoJson(datasetId);
        
        // 지도 데이터 없으면 빈 FeatureCollection 반환
        return geoJson != null ? geoJson : "{\"type\":\"FeatureCollection\",\"features\":[]}";
    }

// 다운로드 파일 변환 과정
    public DownloadExportResultDto downloadDatasetByFormat(
        Long datasetId,
        String format,
        Integer userId,
        String downloadIp
    ){
        // 1. dataset 존재 확인
        DownloadDatasetDetailDto dataset = datasetDownloadMapper.findDatasetDetailById(datasetId);
        if (dataset == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "데이터셋을 찾을 수 없습니다.");
        }

        // 2. 상세와 동일한 권한 정책 사용
        validateDatasetDetailAccess(dataset, userId);

        // 3. format 정규화
        String normalizedFormat = format == null ? "" : format.trim().toUpperCase(Locale.ROOT);

        // 4. 형식별 파일 생성
        DownloadExportResultDto result;
        switch (normalizedFormat) {
            case "CSV":
                result = exportCsv(datasetId, dataset.getTitle());
                break;
            case "GEOJSON":
                result = exportGeoJson(datasetId, dataset.getTitle());
                break;
            case "KML":
                result = exportKml(datasetId, dataset.getTitle());
                break;
            case "SHP":
                // SHP는 1차에선 구조만 잡고, 실제 구현은 GeoTools 같은 라이브러리 연동이 필요
                throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "SHP 다운로드는 준비 중입니다.");
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 형식입니다.");
        }

        // 5. 파일 생성이 성공했을 때만 다운로드 로그/다운로드 수 증가
        // TODO: insertDownloadLog(...) + increaseDownloadCount(...)

        return result;        
    }

    // CSV
    private DownloadExportResultDto exportCsv(Long datasetId, String datasetTitle) {
        List<DatasetFeatureExportDto> features = datasetDownloadMapper.findDatasetFeaturesForExport(datasetId);

        if (features.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "변환할 공간 데이터가 없습니다.");
        }

        // 1. properties 안의 모든 키를 모아서 CSV 헤더 생성
        // 예: name, category, code ...
        LinkedHashSet<String> propertyKeys = new LinkedHashSet<>();
        List<Map<String, Object>> propertyRows = new ArrayList<>();

        for (DatasetFeatureExportDto feature : features) {
            Map<String, Object> properties = parseProperties(feature.getPropertiesJson());
            propertyRows.add(properties);
            propertyKeys.addAll(properties.keySet());
        }

        StringBuilder sb = new StringBuilder();

        // 2. CSV 헤더
        sb.append("feature_id,feature_name,spatial_type,geometry_wkt");
        for (String key : propertyKeys) {
            sb.append(",").append(escapeCsv(key));
        }
        sb.append("\n");

        // 3. CSV 본문
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
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }    
    
    // GeoJson
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

    // KML
    private DownloadExportResultDto exportKml(Long datasetId, String datasetTitle) {
        List<DatasetFeatureExportDto> features = datasetDownloadMapper.findDatasetFeaturesForExport(datasetId);

        if (features.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "변환할 공간 데이터가 없습니다.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        sb.append("<Document>\n");

        for (DatasetFeatureExportDto feature : features) {
            sb.append("  <Placemark>\n");
            sb.append("    <name>").append(escapeXml(feature.getFeatureName())).append("</name>\n");

            // TODO:
            // geometry_json 또는 geometry_wkt를 읽어서
            // Point / LineString / Polygon별 KML 태그로 바꾸는 로직 필요
            // 여기서 1차는 구조만 잡고, 실제 geometry 변환 함수로 분리하는 게 좋음

            sb.append("  </Placemark>\n");
        }

        sb.append("</Document>\n");
        sb.append("</kml>");

        return new DownloadExportResultDto(
                datasetTitle + ".kml",
                "application/vnd.google-earth.kml+xml",
                sb.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
    

}
