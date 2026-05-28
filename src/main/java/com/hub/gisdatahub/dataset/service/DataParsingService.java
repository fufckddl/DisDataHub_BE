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
import com.hub.gisdatahub.dataset.mapper.DatasetMapper;
import com.opencsv.CSVReader;

@Service
public class DataParsingService {

    @Autowired
    @Qualifier("tempFileRootPath")
    private String tempRootPath;    // C:/tempFiles/

    @Autowired
    private DatasetMapper datasetMapper;

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
                // parseExcelData(physicalFile, dto); (일단 보류)
                throw new UnsupportedOperationException("아직 엑셀 파싱은 지원하지 않습니다.");

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
        // TODO: 여기서 GeoJSON 구조를 뜯어서 변환!
        System.out.println("(공사 중) GeoJSON 파일을 뜯어보는 중입니다... 파일명: " + file.getName());
        return 0;
    }

}
