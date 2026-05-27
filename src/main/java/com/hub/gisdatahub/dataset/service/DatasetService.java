package com.hub.gisdatahub.dataset.service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.gisdatahub.dataset.dto.DatasetUploadDto;
import com.hub.gisdatahub.dataset.dto.MyUploadResponseDto;
import com.hub.gisdatahub.dataset.exception.ValidationFailedException;
import com.hub.gisdatahub.dataset.mapper.DatasetMapper;



@Service
public class DatasetService {

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private DataParsingService dataParsingService;

    @Autowired
    private DataValidationService dataValidationService;

    // 자바 객체(Map)를 완벽한 JSON 문자열로 바꿔주는 녀석
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(noRollbackFor = ValidationFailedException.class, rollbackFor = Exception.class)
    public int processUploadData(DatasetUploadDto dto, int userId) throws Exception {
        System.out.println("👨‍🔧 [작업 반장] 플로우차트 시나리오대로 작업을 시작합니다.");

        dto.setCreatedBy(userId);

        formatExtraMetadata(dto);

        // 1️⃣ 파일에서 사이즈, 확장자만 쏙 빼오기
        fileUploadService.extractFileInfo(dto.getFile(), dto);

        // 2️⃣ DB에 4연속 INSERT (이때 XML 쿼리에 의해 경로는 '' 빈칸으로 들어갑니다!)
        datasetMapper.insertDataset(dto);       
        datasetMapper.insertMetadata(dto);      
        datasetMapper.insertUploadLog(dto);     
        datasetMapper.insertDatasetFile(dto);   
        System.out.println("📦 [작업 반장] 빈칸('')을 포함한 DB 초기 적재 완료!");

        // ==========================================================
        // 🚀 태환님 플로우차트 순서 적용! (UPDATE 먼저 -> 물리 저장 나중)
        // ==========================================================

        // 3️⃣ 저장할 UUID 이름과 날짜 폴더 경로를 미리 생성해서 DTO에 꽂아 넣기
        fileUploadService.generateFilePath(dto);

        // 4️⃣ [순서 변경!] 하드디스크에 먼저 물리 파일 적재하기!!! (여기서 체크섬이 계산됨 🚀)
        fileUploadService.savePhysicalFile(dto.getFile(), dto);
        System.out.println("📁 [작업 반장] 하드디스크 물리 파일 적재 및 체크섬 계산 완벽 성공!");

        // 체크섬 검사 (중복 업로드 원천 차단)
        int duplicateCount = datasetMapper.countByChecksum(dto.getChecksum());
        if (duplicateCount > 0) {
            System.out.println("[작업 반장] 중복 파일 적발! 방금 저장한 파일을 즉시 파기합니다.");

            // 1. 방금 하드디스크에 저장했던 파일을 다시 찾아내서 삭제
            File duplicateFile = new File("C:/tempFiles/" + dto.getStoredFilename());
            if (duplicateFile.exists()) {
                duplicateFile.delete();
            }

            // 2. 비상 탈출 (DB는 트랜잭션 덕분에 알아서 롤백됨)
            throw new RuntimeException("이미 시스템에 등록된 동일한 데이터 파일입니다. (중복 업로드 불가)");
        }



        // 5️⃣ [순서 변경!] 파일 저장 및 체크섬 계산이 무사히 끝났으니, 생성된 경로와 체크섬을 DB에 UPDATE 치기!!!
        datasetMapper.updateFileInfo(dto);
        System.out.println("🔄 [작업 반장] sd_gis_dataset_file 테이블 경로 및 체크섬 UPDATE 완료!");

        // ==========================================================
        // 🚀 [제2막 시작] 저장된 원본 파일을 다시 읽어서 파싱 및 Bulk Insert 돌입!
        // ==========================================================
        System.out.println("[작업 반장] 물리 저장 완료! 파싱 전담반에게 데이터 추출을 지시합니다.");
        int totalParsedCount = dataParsingService.parseAndBulkInsert(dto);

        // [제3막 시작] DB에 들어간 데이터를 PostGIS 딥 검증
        System.out.println("[작업 반장] 파싱 완료! 검증 전담반에게 딥 검증을 지시합니다.");
        dataValidationService.validateTempData(dto.getUploadId(), dto.getDatasetId(), dto.getStoredFilename());

        return totalParsedCount;
    }

    // 나의 업로드 내역 조회
    @Transactional(readOnly = true)
    public List<MyUploadResponseDto> getMyUploadList(int userId) {
        System.out.println("[DatasetService] 연구자(" + userId + ")의 업로드 내역 조회를 시작합니다.");

        // Mapper의 고속 LEFT JOIN 쿼리를 호출하여 결과를 바로 리턴
        return datasetMapper.selectMyUploadList(userId);
    }

    // 🛠️ [신규 도우미 메서드] 비정형 텍스트 ➔ JSON 자동 변환기
    private void formatExtraMetadata(DatasetUploadDto dto) {
        String extra = dto.getExtraMetadata();
        
        // 1. 아예 입력 안 했으면 그냥 패스 (DB에 얌전히 null로 들어감)
        if (extra == null || extra.trim().isEmpty()) {
            return;
        }
        
        extra = extra.trim();
        
        // 2. 만약 사용자가 똑똑해서 { } 중괄호를 직접 썼다면? (이미 JSON 모양) -> 건드리지 않고 패스
        if (extra.startsWith("{") && extra.endsWith("}")) {
            return; 
        }

        // 3. 평문 텍스트가 들어왔다면 포장 작업 시작!
        try {
            Map<String, String> jsonMap = new HashMap<>();
            
            if (extra.contains(":")) {
                // 케이스 A: "협력자: 검둥이" 라고 입력했을 때
                // ':' 를 기준으로 첫 번째 조각은 KEY, 두 번째 조각은 VALUE로 쪼갬
                String[] parts = extra.split(":", 2);
                jsonMap.put(parts[0].trim(), parts[1].trim());
            } else {
                // 케이스 B: "검둥이랑 같이 만듦" 처럼 아예 통글자로 썼을 때
                jsonMap.put("기타_입력정보", extra);
            }
            
            // Map을 완벽한 규격의 JSON 문자열 ( {"키":"값"} ) 로 변환! 쌍따옴표 처리 알아서 다 해줍니다.
            String validJson = objectMapper.writeValueAsString(jsonMap);
            dto.setExtraMetadata(validJson);
            
            System.out.println("🔧 [작업 반장] 사용자의 평문을 JSON으로 예쁘게 자동 변환했습니다: " + validJson);
            
        } catch (Exception e) {
            System.err.println("🚨 JSON 변환 실패: " + e.getMessage());
        }
    }
}