package com.hub.gisdatahub.dataset.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.dataset.dto.DatasetUploadDto;
import com.hub.gisdatahub.dataset.dto.TempFeatureDto;
import com.hub.gisdatahub.dataset.exception.ValidationFailedException;
import com.hub.gisdatahub.dataset.mapper.DatasetMapper;
import com.hub.gisdatahub.dataset.mapper.ValidationMapper;
import com.opencsv.CSVReader;
import org.apache.poi.ss.usermodel.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;

import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DataParsingService {

    @Autowired
    @Qualifier("tempFileRootPath")
    private String tempRootPath;    // C:/tempFiles/

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private ValidationMapper validationMapper;

    @Autowired
    private FileUploadService fileUploadService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 플로우차트의 마름모 [파일 확장자?] 판단 로직
    public int parseAndBulkInsert(DatasetUploadDto dto) throws Exception {
        System.out.println("[파싱 전담반] 파일 확장자 판별 및 데이터 추출을 시작합니다.");

        // 1. DTO에 담긴 정보로 하드디스크에 저장된 진짜 파일 객체 찾기
        File physicalFile = new File(tempRootPath + dto.getStoredFilename());

        // 2. 확장자에 따른 파싱 분기 (Routing)
        String extension = dto.getFileExtension();

        switch (extension) {
            case ".csv":
                System.out.println("CSV 파싱 프로세스로 이동합니다.");
                return parseCsvData(physicalFile, dto);
            
            case ".geojson":
            case ".json":
                System.out.println("GeoJSON 파싱 프로세스로 이동합니다.");
                return parseGeoJsonData(physicalFile, dto);

            case ".xlsx":
            case ".xls":
                System.out.println("Excel 파싱 프로세스로 이동합니다.");
                return parseExcelData(physicalFile, dto);

            case ".zip":
            case ".shp":
                System.out.println("SHP(ZIP) 파일 스캔 및 프리패스 프로세스로 이동합니다.");
                return checkShapefileZip(physicalFile, dto);

            default:
                throw new IllegalArgumentException("지원하지 않는 데이터 파일 형식입니다: " + extension);
        }
    }

    private int parseCsvData(File file, DatasetUploadDto dto) {
        
        System.out.println("CSV 파싱을 시작합니다 (최적화 1-Loop 모드)");

        String encoding = dto.getEncoding() != null ? dto.getEncoding() : "UTF-8";
        List<TempFeatureDto> featureList = new ArrayList<>();

        try (
            InputStreamReader isr = new InputStreamReader(new FileInputStream(file), encoding);
            CSVReader csvReader = new CSVReader(isr)
        ) {
            // ==========================================
            // [1단계] 헤더 스캔 및 동적 인덱스 찾기
            // ==========================================
            String[] header = csvReader.readNext();
            if (header == null) throw new RuntimeException("CSV 파일이 비어있습니다.");

            int lonIndex = -1, latIndex = -1, wktIndex = -1;

            // 사용자가 프론트에서 입력한 컬럼명과 일치하는 헤더 위치(Index) 찾기
            for (int i = 0; i < header.length; i++) {
                // 🚀 [버그 수정] 눈에 보이지 않는 UTF-8 BOM 유령 문자 제거 및 공백 제거
                String h = header[i].replace("\uFEFF", "").trim();
                if (h.equalsIgnoreCase(dto.getLonColumnName())) lonIndex = i;
                if (h.equalsIgnoreCase(dto.getLatColumnName())) latIndex = i;
                if (h.equalsIgnoreCase(dto.getWktColumnName())) wktIndex = i;
            }

            // ==========================================
            // [2단계] 단일 반복문 (1-Loop) 추출 및 조립
            // ==========================================
            String[] line;
            int rowIndex = 2;   // 실제 데이터는 2번째 줄부터

            while ((line = csvReader.readNext()) != null) {
                TempFeatureDto feature = new TempFeatureDto();

                feature.setUploadId(dto.getUploadId());
                feature.setRowNumber(rowIndex++);
                feature.setValidationStatus("PENDING");

                // 프론트에서 받아온 카테고리 정보 바로 세팅 (CSV 안 뒤짐)
                // feature.setCategory(dto.getCategory());

                // 핵심 1: JSON으로 섞이기 전에 무조건 0번째 인덱스 값을 featureName에 저장!
                String featureName = line.length > 0 ? line[0] : null;
                feature.setFeatureName(featureName);

                // 핵심 2: 찾아둔 인덱스를 이용해 좌표 안전하게 꺼내기
                String lonStr = (lonIndex != -1 && line.length > lonIndex) ? line[lonIndex] : null;
                String latStr = (latIndex != -1 && line.length > latIndex) ? line[latIndex] : null;

                Double lon = null;
                Double lat = null;
                try {
                    if (lonStr != null && !lonStr.trim().isEmpty()) lon = Double.parseDouble(lonStr.trim());
                    if (latStr != null && !latStr.trim().isEmpty()) lat = Double.parseDouble(latStr.trim());
                } catch (NumberFormatException e) {
                    
                }

                String wkt = (wktIndex != -1 && line.length > wktIndex) ? line[wktIndex] : null;

                // "POINT" 이거나 "MIXED" 일 때 모두 인공호흡기 작동!
                if (("POINT".equals(dto.getSpatialType()) || "MIXED".equals(dto.getSpatialType())) 
                        && (wkt == null || wkt.trim().isEmpty()) 
                        && (lon != null) && (lat != null)) {
                    wkt = "POINT(" + lon + " " + lat + ")";
                }

                feature.setRawLongitude(lon);
                feature.setRawLatitude(lat);
                feature.setRawWkt(wkt);

                // ==========================================
                // 🚀 [추가] WKT 문자열에서 공간 타입(POINT, POLYGON 등) 추출
                // ==========================================
                if (wkt != null && wkt.contains("(")) {
                    // 괄호 '(' 앞부분까지만 자르고, 공백 제거 후 대문자로 변환
                    String extractedType = wkt.substring(0, wkt.indexOf("(")).trim().toUpperCase();
                    feature.setSpatialType(extractedType);
                } else {
                    // WKT가 없거나 잘못된 형태일 경우 일단 비워둠 (추후 자바 2차 검증에서 에러 처리)
                    feature.setSpatialType(null); 
                }

                // 핵심 3: 전체 행을 통쨰로 JSON 압축 (LinkedHashMap으로 원래 순서는 눈으로 보기 좋게 유지)
                Map<String, String> rowDataMap = new LinkedHashMap<>();
                for (int i = 0; i < line.length; i++) {
                    if (i < header.length) {
                        rowDataMap.put(header[i], line[i]);
                    }
                }
                feature.setRawData(objectMapper.writeValueAsString(rowDataMap));

                featureList.add(feature);
            }

            System.out.println(featureList.size() + "개 파싱 완료! Bulk Insert 준비 완료.");

            // ==========================================
            // [3단계] 한 방에 DB로 쏘기 (Bulk Insert)
            // ==========================================
            datasetMapper.bulkInsertTempFeatures(featureList);

            // 임시 적재 완료 직후, 로그 상태를 PROCESSING으로 업데이트!
            datasetMapper.updateUploadStatusToProcessing(dto.getUploadId(), featureList.size());

            System.out.println("Bulk Insert 완벽 성공 및 total_count 반영 완료!");

            return featureList.size();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("CSV 파싱 중 에러 발생: " + e.getMessage());
        }
    }

    private int parseGeoJsonData(File file, DatasetUploadDto dto) {
        System.out.println("GeoJSON 파싱 프로세스를 시작합니다. (스마트 이름 추출 및 WKT 자동 조립 모드)");
        List<TempFeatureDto> featureList = new ArrayList<>();

        try {
            // 1. 파일을 통째로 읽어 안전한 트리(JsonNode) 구조로 변환합니다.
            JsonNode rootNode = objectMapper.readTree(file);
            JsonNode features = rootNode.path("features");

            // 방어막 1: GeoJSON 국제 표준(FeatureCollection) 규격인지 확인
            if (features.isMissingNode() || !features.isArray()) {
                throw new IllegalArgumentException("올바른 GeoJSON 규격이 아닙니다. 'features' 배열을 찾을 수 없습니다.");
            }

            int rowIndex = 1; // GeoJSON은 물리적인 줄 번호가 없으므로 1부터 카운트 (에러 오답 노트용)

            // 2. 데이터(feature) 덩어리를 하나씩 순회하며 파싱
            for (JsonNode featureNode : features) {
                TempFeatureDto feature = new TempFeatureDto();
                feature.setUploadId(dto.getUploadId());
                feature.setRowNumber(rowIndex++);
                feature.setValidationStatus("PENDING");

                JsonNode properties = featureNode.path("properties");
                JsonNode geometry = featureNode.path("geometry");

                // [타겟 1] Properties: 스마트 이름 추출 및 JSON 압축
                if (!properties.isMissingNode() && properties.isObject()) {
                    String extractedName = null;
                    String firstKey = null;

                    // properties 안의 모든 Key를 스캔
                    Iterator<String> fieldNames = properties.fieldNames();
                    while (fieldNames.hasNext()) {
                        String key = fieldNames.next();
                        if (firstKey == null) firstKey = key; // 만약을 대비해 첫 번째 Key를 기억해 둡니다.

                        // '이름' 냄새가 나는 Key를 발견하면 즉시 확보
                        String lowerKey = key.toLowerCase();
                        if (lowerKey.contains("name") || lowerKey.contains("명") || lowerKey.contains("이름")) {
                            extractedName = properties.get(key).asText();
                            break; 
                        }
                    }

                    // 이름과 관련된 Key가 없었다면, 기본적으로 첫 번째 Key의 값을 사용합니다.
                    if (extractedName == null && firstKey != null) {
                        extractedName = properties.get(firstKey).asText();
                    }

                    // 속성 정보가 아예 비어있으면 null, 아니면 추출한 이름 세팅
                    feature.setFeatureName(extractedName != null && !extractedName.trim().isEmpty() ? extractedName.trim() : null);
                    
                    // 나머지 모든 속성들은 원래 JSON 모양 그대로 rawData에 쑤셔 넣습니다!
                    feature.setRawData(properties.toString());
                }

                // [타겟 2] Geometry: 공간 타입 추출 및 WKT 강제 조립
                if (!geometry.isMissingNode() && geometry.isObject()) {
                    // GeoJSON의 공간 타입(Point, Polygon 등)을 무조건 대문자로 추출
                    String geoType = geometry.path("type").asText().toUpperCase(); 
                    JsonNode coordinates = geometry.path("coordinates");
                    
                    feature.setSpatialType(geoType);
                    
                    // 배열 형태의 좌표 [127, 37]를 -> "POINT(127 37)" 문자열로 강제 변환
                    String wkt = convertCoordinatesToWkt(geoType, coordinates);
                    feature.setRawWkt(wkt);
                } else {
                    // geometry 방이 아예 없으면 rawWkt는 null이 되고, 이후 DB Mapper에서 "공간 데이터 누락" 에러로 자동 적발됩니다.
                    feature.setRawWkt(null);
                    feature.setSpatialType(null);
                }

                featureList.add(feature);
            }

            // ==========================================
            // [마무리] DB 일괄 저장 (Bulk Insert)
            // ==========================================
            if (!featureList.isEmpty()) {
                datasetMapper.bulkInsertTempFeatures(featureList);
                datasetMapper.updateUploadStatusToProcessing(dto.getUploadId(), featureList.size());
            }

            System.out.println("GeoJSON 데이터 " + featureList.size() + "개 파싱 완료 및 Bulk Insert 성공!");
            return featureList.size();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("GeoJSON 파싱 중 에러 발생: " + e.getMessage());
        }
    }

    // GeoJSON 배열 좌표를 ➔ WKT 텍스트로 변환
    private String convertCoordinatesToWkt(String type, JsonNode coords) {
        // 좌표 배열이 정상이 아니면 null 반환 (DB 검증에서 INVALID_GEOMETRY로 잡아냄)
        if (coords.isMissingNode() || !coords.isArray()) return null;

        StringBuilder wkt = new StringBuilder(type); // 예: POINT, LINESTRING, POLYGON
        
        try {
            if ("POINT".equals(type)) {
                // POINT(127.123 37.123)
                wkt.append("(").append(coords.get(0).asText()).append(" ").append(coords.get(1).asText()).append(")");
            
            } else if ("LINESTRING".equals(type)) {
                // LINESTRING(127 37, 128 38)
                wkt.append("(");
                for (int i = 0; i < coords.size(); i++) {
                    JsonNode pt = coords.get(i);
                    wkt.append(pt.get(0).asText()).append(" ").append(pt.get(1).asText());
                    if (i < coords.size() - 1) wkt.append(", ");
                }
                wkt.append(")");
            
            } else if ("POLYGON".equals(type)) {
                // POLYGON((127 37, 128 38, ...)) ➔ GeoJSON은 다중 링(구멍 뚫린 폴리곤)을 지원하므로 반복문을 이중으로 돕니다.
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
                return null; // POINT, LINESTRING, POLYGON 외의 값(MultiPolygon 등)은 현재 처리하지 않고 null 반환
            }
            return wkt.toString();
        } catch (Exception e) {
            // 좌표 배열 안에 숫자가 없거나 구조가 깨진 경우 조용히 null을 반환하여, Mapper의 '형태 불량' 검증에 걸리게 유도합니다.
            return null; 
        }
    }

    private int parseExcelData(File file, DatasetUploadDto dto) {
        System.out.println("Excel 파싱을 시작합니다 (DataFormatter 문자열 강제 변환 모드)");
        List<TempFeatureDto> featureList = new ArrayList<>();
        
        // 🚀 [마법 지팡이] 엑셀 셀의 타입(숫자, 날짜, 수식)을 무시하고 눈에 보이는 그대로의 텍스트로 뽑아주는 객체
        DataFormatter formatter = new DataFormatter();

        try (
            FileInputStream fis = new FileInputStream(file);
            Workbook workbook = WorkbookFactory.create(fis)
        ) {
            // ==========================================
            // [1] 시트 락온 (태환님 아이디어 100% 적용)
            // ==========================================
            String targetSheetName = dto.getSheetName();
            
            // 방어막 1: 사용자가 시트 이름을 안 적었을 때
            if (targetSheetName == null || targetSheetName.trim().isEmpty()) {
                throw new IllegalArgumentException("엑셀 파일 파싱을 위한 '시트 이름'이 입력되지 않았습니다.");
            }

            Sheet sheet = workbook.getSheet(targetSheetName);

            // 방어막 2: 엑셀 파일 안에 해당 이름의 시트가 없을 때
            if (sheet == null) {
                throw new IllegalArgumentException("엑셀 파일에 '" + targetSheetName + "' 시트가 존재하지 않습니다. 시트 이름을 다시 확인해 주세요.");
            }

            // ==========================================
            // [2] 헤더 스캔 및 동적 인덱스 찾기 (CSV 로직 이식)
            // ==========================================
            Row headerRow = sheet.getRow(0); // 0번째 줄이 무조건 헤더라고 가정
            if (headerRow == null) throw new RuntimeException("엑셀 파일의 첫 번째 줄(헤더)이 비어있습니다.");

            int lonIndex = -1, latIndex = -1, wktIndex = -1;
            int lastCellNum = headerRow.getLastCellNum(); // 총 컬럼 개수
            String[] headerNames = new String[lastCellNum];

            for (int i = 0; i < lastCellNum; i++) {
                Cell cell = headerRow.getCell(i);
                // 마법 지팡이로 셀 값을 String으로 강제 변환
                String headerName = (cell == null) ? "" : formatter.formatCellValue(cell).trim();
                headerNames[i] = headerName;

                if (headerName.equalsIgnoreCase(dto.getLonColumnName())) lonIndex = i;
                if (headerName.equalsIgnoreCase(dto.getLatColumnName())) latIndex = i;
                if (headerName.equalsIgnoreCase(dto.getWktColumnName())) wktIndex = i;
            }

            // ==========================================
            // [3] 단일 반복문 파싱 및 조립
            // ==========================================
            int rowIndex = 2; // 원본 엑셀의 줄 번호 (헤더가 1번줄이므로 데이터는 2번줄부터)
            
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);

                // 🚨 방어막 3: 유령 행(Ghost Row) 스킵. 사용자가 지웠지만 엑셀이 기억하는 빈 줄 건너뛰기
                if (row == null || row.getCell(0) == null || formatter.formatCellValue(row.getCell(0)).trim().isEmpty()) {
                    continue; 
                }

                TempFeatureDto feature = new TempFeatureDto();
                feature.setUploadId(dto.getUploadId());
                feature.setRowNumber(rowIndex++);
                feature.setValidationStatus("PENDING");

                // 0번째 인덱스는 무조건 featureName (CSV와 동일 규칙)
                feature.setFeatureName(formatter.formatCellValue(row.getCell(0)).trim());

                // 좌표 데이터 꺼내기 (인덱스가 존재하고 셀이 비어있지 않을 때만)
                String lonStr = (lonIndex != -1 && row.getCell(lonIndex) != null) ? formatter.formatCellValue(row.getCell(lonIndex)).trim() : null;
                String latStr = (latIndex != -1 && row.getCell(latIndex) != null) ? formatter.formatCellValue(row.getCell(latIndex)).trim() : null;

                Double lon = null; Double lat = null;
                try {
                    if (lonStr != null && !lonStr.isEmpty()) lon = Double.parseDouble(lonStr);
                    if (latStr != null && !latStr.isEmpty()) lat = Double.parseDouble(latStr);
                } catch (NumberFormatException e) { /* 숫자 변환 실패 시 조용히 넘어감 -> 2차 공간 검증에서 잡아냄 */ }

                String wkt = (wktIndex != -1 && row.getCell(wktIndex) != null) ? formatter.formatCellValue(row.getCell(wktIndex)).trim() : null;

                // 🚀 방어막 4: WKT 인공호흡기 (CSV 로직 완벽 복사)
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

                // 방어막 5: JSON 블랙홀 압축 (LinkedHashMap 유지)
                Map<String, String> rowDataMap = new LinkedHashMap<>();
                for (int i = 0; i < lastCellNum; i++) {
                    Cell cell = row.getCell(i);
                    String cellValue = (cell == null) ? "" : formatter.formatCellValue(cell).trim();
                    rowDataMap.put(headerNames[i], cellValue);
                }
                feature.setRawData(objectMapper.writeValueAsString(rowDataMap));

                featureList.add(feature);
            }

            // ==========================================
            // [4] DB 일괄 저장 (Bulk Insert)
            // ==========================================
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

    private int checkShapefileZip(File zipFile, DatasetUploadDto dto) throws Exception {
        
        // 1. 우리가 반드시 찾아야 할 SHP 필수 확장자 5대장 세팅
        Set<String> requiredExtensions = new HashSet<>(Arrays.asList(".shp", ".shx", ".dbf", ".prj", ".cpg"));
        Set<String> foundExtensions = new HashSet<>();

        // 2. 파일 이름 인코딩 설정 (한글 파일명 에러 방지용)
        String encoding = dto.getEncoding() != null ? dto.getEncoding() : "UTF-8";

        // 3. ZIP 파일 스캔 시작 (물리적으로 압축을 풀지 않고 파일 이름만 추출)
        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis, Charset.forName(encoding))) {

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
                // 인코딩이 안 맞아서 에러가 났을 경우 (주로 UTF-8 파일에 EUC-KR을 먹였을 때)
                throw new RuntimeException("ZIP 파일 스캔 실패! 압축 파일 내부의 한글 파일명이 깨졌습니다. 인코딩 설정을 변경해 보세요.");
            }

            // 4. 필수 확장자 5개가 모두 발견되었는지 검사 (requiredExtensions에서 발견된 것을 뺌)
            requiredExtensions.removeAll(foundExtensions);

            if (requiredExtensions.isEmpty()) {
                System.out.println("[SHP 검사] 필수 5개 파일 모두 확인 완료! 프리패스 승인합니다.");

                // 프리패스 전용 상태 업데이트 쿼리 실행
                datasetMapper.updateLogForDirectPass(dto.getUploadId());
                validationMapper.updateDatasetStatusToRequest(dto.getDatasetId());

                return 0;
            } else {
                String errorMessage = "Shapefile 묶음 안에 필수 파일이 누락되었습니다. 누락된 파일: " + requiredExtensions;
                System.err.println("🚨 [SHP 검사 실패] " + errorMessage);

                // 1. 관제탑(sd_upload_log) 에러 상태 기록 (errorCount는 파일 1개 뭉치이므로 1로 세팅)
                validationMapper.updateLogValidationFailed(dto.getUploadId(), 1);

                // 2. 파일 메타데이터(sd_gis_dataset_file) 경로 초기화 (빈 문자열)
                validationMapper.clearDatasetFilePath(dto.getDatasetId());

                // 3. 부모 테이블(sd_gis_dataset) 최종 상태 INVALID 처리
                validationMapper.updateDatasetStatusToInvalid(dto.getDatasetId());

                // 4. 하드디스크 용량 확보! 불량 ZIP 파일 즉시 영구 파기 (OS 레벨)
                fileUploadService.deleteTempFile(dto.getStoredFilename());
                System.out.println("🗑️ [하드 롤백] 불량 ZIP 물리 파일 삭제 완료.");

                // 5. [핵심] RuntimeException 대신 ValidationFailedException 던지기!
                // 이 예외는 @Transactional(noRollbackFor)에 등록되어 있어서 DB 롤백을 막아줍니다.
                throw new ValidationFailedException(errorMessage, dto.getUploadId());
            }
    }
}
