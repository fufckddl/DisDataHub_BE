package com.hub.gisdatahub.dataset.service;

import java.io.InputStream;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hub.gisdatahub.dataset.dto.DatasetUploadDto;
import com.hub.gisdatahub.s3.dto.S3ObjectResult;
import com.hub.gisdatahub.s3.service.S3FileService;

@Service
public class FileUploadService {

    // 🚀 C드라이브 설정(FileConfig)을 다 버리고, S3 전담 요원을 고용합니다!
    @Autowired
    private S3FileService s3FileService; 

    // 1️⃣ 파일 기본 정보와 암호만 추출해서 DTO에 담기 (변경 없음)
    public void extractFileInfo(MultipartFile file, DatasetUploadDto dto) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 비어있거나 존재하지 않습니다.");
        }
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        dto.setOriginalFilename(originalFilename);
        dto.setFileExtension(fileExtension);
        dto.setFileSize(file.getSize());
        dto.setMimeType(file.getContentType());
    }

    // 2️⃣ DTO 가짜 경로 꽂아넣기 (DatasetService의 기존 흐름을 깨지 않기 위한 더미 데이터)
    public void generateFilePath(DatasetUploadDto dto) {
        // S3 요원이 업로드 시점에 알아서 "2026/06/02/uuid.csv"를 생성해주지만, 
        // 3번 스텝이 에러 나지 않도록 가짜 값을 세팅해 둡니다.
        // (이 값은 바로 아래 savePhysicalFile에서 진짜 S3 경로로 완벽하게 덮어씌워집니다!)
        dto.setStoredFilename("temp-ready-to-s3" + dto.getFileExtension());
        dto.setFilePath("tempFiles");
    }

    // 3️⃣ 진짜 S3 업로드 및 메모리 안전(OOM 방지) 체크섬 계산
    public void savePhysicalFile(MultipartFile file, DatasetUploadDto dto) throws Exception {
        System.out.println("☁️ [S3 방패] OOM 방지용 안전 스트림으로 체크섬 계산을 시작합니다.");
        
        // 1. 메모리 폭발 방지! 빨대(InputStream)를 꽂아 8KB씩 읽어서 체크섬 계산
        String checksum = calculateChecksumSafely(file);
        dto.setChecksum(checksum);

        System.out.println("☁️ [S3 방패] 체크섬 계산 완료. S3 클라우드로 파일 업로드를 지시합니다.");
        
        // 2. S3 요원에게 업로드 지시 (이 안에서 UUID와 날짜 폴더가 자동 생성됨)
        S3ObjectResult result = s3FileService.uploadToTempFiles(file);

        // 3. S3 요원이 발급한 진짜 클라우드 경로와 파일명으로 DTO 덮어쓰기!
        dto.setStoredFilename(result.storedFilename());
        dto.setFilePath(result.filePath());
        
        System.out.println("☁️ [S3 방패] S3 업로드 성공! 부여된 경로: " + result.filePath() + "/" + result.storedFilename());
    }

    // 🚀 [핵심 뇌관 해체] 서버가 절대 뻗지 않는 안전한 체크섬 계산기
    private String calculateChecksumSafely(MultipartFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        // 🚨 file.getBytes()는 2GB 파일을 통째로 RAM에 올리므로 절대 금지! 
        // InputStream을 열어서 8KB 단위로 쪼개서 마십니다.
        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // ==========================================
    // 🚀 관리자 파이프라인 전용 방패 메서드들 (S3 연결 완벽 처리)
    // ==========================================

    // 1. Temp 폴더에서 Upload 폴더로 안전하게 '복사(Copy)'
    public void copyToUploadFolder(String storedFilename) throws Exception {
        // S3 요원에게 클라우드 내부 복사 지시! (네트워크 이동 없이 AWS 내부에서 1초 컷)
        s3FileService.copyTempFileToUploadFiles(storedFilename);
    }

    // 2. (모든 DB 성공 시) Temp 폴더의 원본 찌꺼기 파일 삭제
    public void deleteTempFile(String storedFilename) {
        try {
            // S3 요원에게 'tempFiles' 소속이라고 정확히 짚어주며 삭제 지시!
            s3FileService.deleteFile("tempFiles", storedFilename);
            System.out.println("tempFiles에서 원본 파일 삭제 성공");
        } catch (Exception e) {
            System.err.println("☁️ S3 Temp 파일 삭제 실패! 관리자 콘솔 확인 필요: " + storedFilename);
        }
    }

    // 3. (에러 롤백 시) Upload 폴더에 잘못 복사된 파일 파기
    public void deleteUploadFile(String storedFilename) {
        try {
            // S3 요원에게 'uploadFiles' 소속이라고 정확히 짚어주며 삭제 지시!
            s3FileService.deleteFile("uploadFiles", storedFilename);
        } catch (Exception e) {
            System.err.println("☁️ S3 Upload 롤백 파일 삭제 실패! 관리자 콘솔 확인 필요: " + storedFilename);
        }
    }
}