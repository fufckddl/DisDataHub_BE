package com.hub.gisdatahub.dataset.service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.dataset.dto.DatasetUploadDto;
import com.hub.gisdatahub.dataset.dto.TempFeatureDto;
import com.hub.gisdatahub.dataset.exception.ValidationFailedException;
import com.hub.gisdatahub.dataset.mapper.DatasetMapper;
import com.hub.gisdatahub.dataset.mapper.ValidationMapper;
import com.hub.gisdatahub.s3.service.S3FileService;
import com.opencsv.CSVReader;
import org.apache.poi.ss.usermodel.*;

@Service
public class DataParsingService {

    // 🚀 [수정] C드라이브 경로(tempRootPath) 퇴출! 대신 S3 요원을 고용합니다.
    @Autowired
    private S3FileService s3FileService;

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private ValidationMapper validationMapper;

    @Autowired
    private FileUploadService fileUploadService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public int parseAndBulkInsert(DatasetUploadDto dto) throws Exception {
        System.out.println("[파싱 전담반] 파일 확장자 판별 및 데이터 추출을 시작합니다.");

        long maxSizeBytes = 2L * 1024 * 1024 * 1024;

        if (dto.getFileSize() > maxSizeBytes) {
            String errorMessage = "파일 용량 제한(2GB)을 초과했습니다. (현재 크기: " + (dto.getFileSize() / 1024 / 1024) + "MB)";
            System.err.println("🚨 [백엔드 용량 초과 차단] " + errorMessage);

            validationMapper.updateLogValidationFailed(dto.getUploadId(), 1);
            validationMapper.clearDatasetFilePath(dto.getDatasetId());
            validationMapper.updateDatasetStatusToInvalid(dto.getDatasetId());

            // 🚀 FileUploadService가 이미 S3로 연결되어 있으므로, 클라우드의 2GB 넘는 파일이 여기서 안전하게 지워집니다!
            fileUploadService.deleteTempFile(dto.getStoredFilename());
            System.out.println("🗑️ [클라우드 롤백] 2GB 초과 S3 파일 삭제 완료.");

            throw new ValidationFailedException(errorMessage, dto.getUploadId());
        }

        // 🚀 [수정] File 객체를 만들어서 넘기던 로직 삭제!
        // 대신 파서들이 각자 S3에서 빨대(Stream)를 꽂도록 dto만 넘겨줍니다.
        String extension = dto.getFileExtension();

        switch (extension) {
            case ".csv":
                System.out.println("CSV 파싱 프로세스로 이동합니다.");
                return parseCsvData(dto);
            
            case ".geojson":
            case ".json":
                System.out.println("GeoJSON 파싱 프로세스로 이동합니다.");
                return parseGeoJsonData(dto);

            case ".xlsx":
            case ".xls":
                System.out.println("Excel 파싱 프로세스로 이동합니다.");
                return parseExcelData(dto);

            case ".zip":
            case ".shp":
                System.out.println("SHP(ZIP) 파일 스캔 및 프리패스 프로세스로 이동합니다.");
                return checkShapefileZip(dto);

            case ".tif":
            case ".tiff":
                System.out.println("TIFF 파일 확인. 프리패스 프로세스로 이동합니다.");
                return processTiffPass(dto);

            default:
                throw new IllegalArgumentException("지원하지 않는 데이터 파일 형식입니다: " + extension);
        }
    }

    private int parseCsvData(DatasetUploadDto dto) {
        System.out.println("CSV 파싱을 시작합니다 (S3 스트리밍 모드)");

        String encoding = dto.getEncoding() != null ? dto.getEncoding() : "UTF-8";
        List<TempFeatureDto> featureList = new ArrayList<>();

        // 🚀 [핵심 수술] FileInputStream 대신 s3FileService.downloadFileAsStream 사용!
        // try-with-resources 안에 선언했으므로, 파싱이 끝나면 Java가 알아서 S3 연결(빨대)을 끊어줍니다. (메모리 릭 방지)
        try (
            InputStream is = s3FileService.downloadFileAsStream("tempFiles", dto.getStoredFilename());
            InputStreamReader isr = new InputStreamReader(is, encoding);
            CSVReader csvReader = new CSVReader(isr)
        ) {
            String[] header = csvReader.readNext();
            if (header == null) throw new RuntimeException("CSV 파일이 비어있습니다.");

            int lonIndex = -1, latIndex = -1, wktIndex = -1;

            for (int i = 0; i < header.length; i++) {
                String h = header[i].replace("\uFEFF", "").trim();
                if (h.equalsIgnoreCase(dto.getLonColumnName())) lonIndex = i;
                if (h.equalsIgnoreCase(dto.getLatColumnName())) latIndex = i;
                if (h.equalsIgnoreCase(dto.getWktColumnName())) wktIndex = i;
            }

            String[] line;
            int rowIndex = 2;

            while ((line = csvReader.readNext()) != null) {
                TempFeatureDto feature = new TempFeatureDto();

                feature.setUploadId(dto.getUploadId());
                feature.setRowNumber(rowIndex++);
                feature.setValidationStatus("PENDING");

                String featureName = line.length > 0 ? line[0] : null;
                feature.setFeatureName(featureName);

                String lonStr = (lonIndex != -1 && line.length > lonIndex) ? line[lonIndex] : null;
                String latStr = (latIndex != -1 && line.length > latIndex) ? line[latIndex] : null;

                Double lon = null;
                Double lat = null;
                try {
                    if (lonStr != null && !lonStr.trim().isEmpty()) lon = Double.parseDouble(lonStr.trim());
                    if (latStr != null && !latStr.trim().isEmpty()) lat = Double.parseDouble(latStr.trim());
                } catch (NumberFormatException e) { }

                String wkt = (wktIndex != -1 && line.length > wktIndex) ? line[wktIndex] : null;

                if (("POINT".equals(dto.getSpatialType()) || "MIXED".equals(dto.getSpatialType())) 
                        && (wkt == null || wkt.trim().isEmpty()) 
                        && (lon != null) && (lat != null)) {
                    wkt = "POINT(" + lon + " " + lat + ")";
                }

                feature.setRawLongitude(lon);
                feature.setRawLatitude(lat);
                feature.setRawWkt(wkt);

                if (wkt != null && wkt.contains("(")) {
                    String extractedType = wkt.substring(0, wkt.indexOf("(")).trim().toUpperCase();
                    feature.setSpatialType(extractedType);
                } else {
                    feature.setSpatialType(null); 
                }

                Map<String, String> rowDataMap = new LinkedHashMap<>();
                for (int i = 0; i < line.length; i++) {
                    if (i < header.length) {
                        rowDataMap.put(header[i], line[i]);
                    }
                }
                feature.setRawData(objectMapper.writeValueAsString(rowDataMap));

                featureList.add(feature);
            }

            datasetMapper.bulkInsertTempFeatures(featureList);
            datasetMapper.updateUploadStatusToProcessing(dto.getUploadId(), featureList.size());
            System.out.println("Bulk Insert 완벽 성공 및 total_count 반영 완료!");

            return featureList.size();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("CSV 파싱 중 에러 발생: " + e.getMessage());
        }
    }

    private int parseGeoJsonData(DatasetUploadDto dto) {
        System.out.println("GeoJSON 파싱 프로세스를 시작합니다. (S3 스트리밍 모드)");
        List<TempFeatureDto> featureList = new ArrayList<>();

        // 🚀 [수술] File 객체 대신 S3 스트림 투입
        try (InputStream is = s3FileService.downloadFileAsStream("tempFiles", dto.getStoredFilename())) {
            
            JsonNode rootNode = objectMapper.readTree(is);
            JsonNode features = rootNode.path("features");

            if (features.isMissingNode() || !features.isArray()) {
                throw new IllegalArgumentException("올바른 GeoJSON 규격이 아닙니다. 'features' 배열을 찾을 수 없습니다.");
            }

            int rowIndex = 1; 

            for (JsonNode featureNode : features) {
                TempFeatureDto feature = new TempFeatureDto();
                feature.setUploadId(dto.getUploadId());
                feature.setRowNumber(rowIndex++);
                feature.setValidationStatus("PENDING");

                JsonNode properties = featureNode.path("properties");
                JsonNode geometry = featureNode.path("geometry");

                if (!properties.isMissingNode() && properties.isObject()) {
                    String extractedName = null;
                    String firstKey = null;

                    Iterator<String> fieldNames = properties.fieldNames();
                    while (fieldNames.hasNext()) {
                        String key = fieldNames.next();
                        if (firstKey == null) firstKey = key;

                        String lowerKey = key.toLowerCase();
                        if (lowerKey.contains("name") || lowerKey.contains("명") || lowerKey.contains("이름") || lowerKey.contains("title")) {
                            extractedName = properties.get(key).asText();
                            break; 
                        }
                    }

                    if (extractedName == null && firstKey != null) {
                        extractedName = properties.get(firstKey).asText();
                    }

                    feature.setFeatureName(extractedName != null && !extractedName.trim().isEmpty() ? extractedName.trim() : null);
                    feature.setRawData(properties.toString());
                }

                if (!geometry.isMissingNode() && geometry.isObject()) {
                    String geoType = geometry.path("type").asText().toUpperCase(); 
                    JsonNode coordinates = geometry.path("coordinates");
                    
                    feature.setSpatialType(geoType);
                    String wkt = convertCoordinatesToWkt(geoType, coordinates);
                    feature.setRawWkt(wkt);
                } else {
                    feature.setRawWkt(null);
                    feature.setSpatialType(null);
                }

                featureList.add(feature);
            }

            if (!featureList.isEmpty()) {
                datasetMapper.bulkInsertTempFeatures(featureList);
                datasetMapper.updateUploadStatusToProcessing(dto.getUploadId(), featureList.size());
            }

            return featureList.size();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("GeoJSON 파싱 중 에러 발생: " + e.getMessage());
        }
    }

    private String convertCoordinatesToWkt(String type, JsonNode coords) {
        if (coords.isMissingNode() || !coords.isArray()) return null;
        StringBuilder wkt = new StringBuilder(type); 
        try {
            if ("POINT".equals(type)) {
                wkt.append("(").append(coords.get(0).asText()).append(" ").append(coords.get(1).asText()).append(")");
            } else if ("LINESTRING".equals(type)) {
                wkt.append("(");
                for (int i = 0; i < coords.size(); i++) {
                    JsonNode pt = coords.get(i);
                    wkt.append(pt.get(0).asText()).append(" ").append(pt.get(1).asText());
                    if (i < coords.size() - 1) wkt.append(", ");
                }
                wkt.append(")");
            } else if ("POLYGON".equals(type)) {
                wkt.append("(");
                for (int i = 0; i < coords.size(); i++) {
                    JsonNode ring = coords.get(i);
                    wkt.append("(");
                    for (int j = 0; j < ring.size(); j++) {
                        JsonNode pt = ring.get(j);
                        wkt.append(pt.get(0).asText()).append(" ").append(pt.get(1).asText());
                        if (j < ring.size() - 1) wkt.append(", ");
                    }
                    wkt.append(")");
                    if (i < coords.size() - 1) wkt.append(", ");
                }
                wkt.append(")");
            } else {
                return null; 
            }
            return wkt.toString();
        } catch (Exception e) {
            return null; 
        }
    }

    private int parseExcelData(DatasetUploadDto dto) {
        System.out.println("Excel 파싱을 시작합니다 (S3 스트리밍 모드)");
        List<TempFeatureDto> featureList = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        // 🚀 [수술] FileInputStream 대신 S3 스트림 사용!
        try (
            InputStream is = s3FileService.downloadFileAsStream("tempFiles", dto.getStoredFilename());
            Workbook workbook = WorkbookFactory.create(is)
        ) {
            String targetSheetName = dto.getSheetName();
            if (targetSheetName == null || targetSheetName.trim().isEmpty()) {
                throw new IllegalArgumentException("엑셀 파일 파싱을 위한 '시트 이름'이 입력되지 않았습니다.");
            }

            Sheet sheet = workbook.getSheet(targetSheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("엑셀 파일에 '" + targetSheetName + "' 시트가 존재하지 않습니다. 시트 이름을 다시 확인해 주세요.");
            }

            Row headerRow = sheet.getRow(0); 
            if (headerRow == null) throw new RuntimeException("엑셀 파일의 첫 번째 줄(헤더)이 비어있습니다.");

            int lonIndex = -1, latIndex = -1, wktIndex = -1;
            int lastCellNum = headerRow.getLastCellNum(); 
            String[] headerNames = new String[lastCellNum];

            for (int i = 0; i < lastCellNum; i++) {
                Cell cell = headerRow.getCell(i);
                String headerName = (cell == null) ? "" : formatter.formatCellValue(cell).trim();
                headerNames[i] = headerName;

                if (headerName.equalsIgnoreCase(dto.getLonColumnName())) lonIndex = i;
                if (headerName.equalsIgnoreCase(dto.getLatColumnName())) latIndex = i;
                if (headerName.equalsIgnoreCase(dto.getWktColumnName())) wktIndex = i;
            }

            int rowIndex = 2; 
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || row.getCell(0) == null || formatter.formatCellValue(row.getCell(0)).trim().isEmpty()) {
                    continue; 
                }

                TempFeatureDto feature = new TempFeatureDto();
                feature.setUploadId(dto.getUploadId());
                feature.setRowNumber(rowIndex++);
                feature.setValidationStatus("PENDING");

                feature.setFeatureName(formatter.formatCellValue(row.getCell(0)).trim());

                String lonStr = (lonIndex != -1 && row.getCell(lonIndex) != null) ? formatter.formatCellValue(row.getCell(lonIndex)).trim() : null;
                String latStr = (latIndex != -1 && row.getCell(latIndex) != null) ? formatter.formatCellValue(row.getCell(latIndex)).trim() : null;

                Double lon = null; Double lat = null;
                try {
                    if (lonStr != null && !lonStr.isEmpty()) lon = Double.parseDouble(lonStr);
                    if (latStr != null && !latStr.isEmpty()) lat = Double.parseDouble(latStr);
                } catch (NumberFormatException e) { }

                String wkt = (wktIndex != -1 && row.getCell(wktIndex) != null) ? formatter.formatCellValue(row.getCell(wktIndex)).trim() : null;

                if (("POINT".equals(dto.getSpatialType()) || "MIXED".equals(dto.getSpatialType())) 
                        && (wkt == null || wkt.isEmpty()) && (lon != null) && (lat != null)) {
                    wkt = "POINT(" + lon + " " + lat + ")";
                }

                feature.setRawLongitude(lon);
                feature.setRawLatitude(lat);
                feature.setRawWkt(wkt);

                if (wkt != null && wkt.contains("(")) {
                    feature.setSpatialType(wkt.substring(0, wkt.indexOf("(")).trim().toUpperCase());
                } else {
                    feature.setSpatialType(null); 
                }

                Map<String, String> rowDataMap = new LinkedHashMap<>();
                for (int i = 0; i < lastCellNum; i++) {
                    Cell cell = row.getCell(i);
                    String cellValue = (cell == null) ? "" : formatter.formatCellValue(cell).trim();
                    rowDataMap.put(headerNames[i], cellValue);
                }
                feature.setRawData(objectMapper.writeValueAsString(rowDataMap));

                featureList.add(feature);
            }

            if (!featureList.isEmpty()) {
                datasetMapper.bulkInsertTempFeatures(featureList);
                datasetMapper.updateUploadStatusToProcessing(dto.getUploadId(), featureList.size());
            }

            return featureList.size();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Excel 파싱 중 치명적 에러 발생: " + e.getMessage());
        }
    }

    private int checkShapefileZip(DatasetUploadDto dto) throws Exception {
        Set<String> requiredExtensions = new HashSet<>(Arrays.asList(".shp", ".shx", ".dbf", ".prj", ".cpg"));
        Set<String> foundExtensions = new HashSet<>();
        String encoding = dto.getEncoding() != null ? dto.getEncoding() : "UTF-8";

        // 🚀 [수술] ZIP 압축 파일도 S3 스트림으로 실시간 스캔! (임시 다운로드 불필요)
        try (
             InputStream is = s3FileService.downloadFileAsStream("tempFiles", dto.getStoredFilename());
             ZipInputStream zis = new ZipInputStream(is, Charset.forName(encoding))
        ) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String fileName = entry.getName().toLowerCase();
                    int lastDotIndex = fileName.lastIndexOf(".");
                    if (lastDotIndex != -1) {
                        foundExtensions.add(fileName.substring(lastDotIndex));
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("ZIP 파일 스캔 실패! 압축 파일 내부의 한글 파일명이 깨졌습니다. 인코딩 설정을 변경해 보세요.");
        }

        requiredExtensions.removeAll(foundExtensions);

        if (requiredExtensions.isEmpty()) {
            System.out.println("[SHP 검사] 필수 5개 파일 모두 확인 완료! 프리패스 승인합니다.");
            datasetMapper.updateLogForDirectPass(dto.getUploadId());
            validationMapper.updateDatasetStatusToRequest(dto.getDatasetId());
            return 0;
        } else {
            String errorMessage = "Shapefile 묶음 안에 필수 파일이 누락되었습니다. 누락된 파일: " + requiredExtensions;
            System.err.println("🚨 [SHP 검사 실패] " + errorMessage);

            validationMapper.updateLogValidationFailed(dto.getUploadId(), 1);
            validationMapper.clearDatasetFilePath(dto.getDatasetId());
            validationMapper.updateDatasetStatusToInvalid(dto.getDatasetId());

            fileUploadService.deleteTempFile(dto.getStoredFilename());
            System.out.println("🗑️ [클라우드 롤백] 불량 ZIP 파일 삭제 완료.");

            throw new ValidationFailedException(errorMessage, dto.getUploadId());
        }
    }

    private int processTiffPass(DatasetUploadDto dto) {
        System.out.println("[TIFF 프리패스] 공간 데이터 파싱 및 검증 생략. 승인 대기(REQUEST) 상태로 직행합니다.");
        datasetMapper.updateLogForDirectPass(dto.getUploadId());
        validationMapper.updateDatasetStatusToRequest(dto.getDatasetId());
        return 0;
    }
}