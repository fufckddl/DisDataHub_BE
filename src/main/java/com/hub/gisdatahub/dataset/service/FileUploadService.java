package com.hub.gisdatahub.dataset.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.hub.gisdatahub.dataset.dto.DatasetUploadDto;

@Service
public class FileUploadService {

    @Autowired
    @Qualifier("tempFileRootPath")
    private String tempRootPath; // C:/tempFiles/

    @Autowired
    @Qualifier("uploadFileRootPath")
    private String uploadRootPath; // C:/uploadFiles/

    // 1️⃣ 파일 기본 정보와 암호만 추출해서 DTO에 담기
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

    // 2️⃣ [신규 추가] 저장할 경로와 UUID 파일명만 미리 생성해서 DTO에 담기 (UPDATE 용도)
    public void generateFilePath(DatasetUploadDto dto) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd/");
        String datePath = sdf.format(new Date()); // 예: 2026/05/19/
        
        String uuidName = UUID.randomUUID().toString();
        String storedFilename = datePath + uuidName + dto.getFileExtension(); 
        
        dto.setStoredFilename(storedFilename);
        dto.setFilePath("tempFiles");
    }

    // 3️⃣ DTO에 담긴 경로를 보고 실제로 하드디스크에 폴더 파고 파일 저장하기
    public void savePhysicalFile(MultipartFile file, DatasetUploadDto dto) throws Exception {
        // DTO에 있는 "2026/05/19/uuid.csv" 문자열을 이용해 물리 파일 객체 생성
        File targetFile = new File(tempRootPath + dto.getStoredFilename());
        
        // 상위 폴더(2026/05/19)가 없으면 생성
        File directory = targetFile.getParentFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        // 실제 파일 전송!
        file.transferTo(targetFile);

        String checksum = calculateChecksum(targetFile);
        dto.setChecksum(checksum);
    }

    private String calculateChecksum(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new FileInputStream(file)) {
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

    // 관리자 최종 승인 파이프라인 정용 메서드

    // 1. Temp 폴더에서 Upload 폴더로 안전하게 '복사(Copy)'
    public void copyToUploadFolder(String storedFilename) throws Exception {
        File tempFile = new File(tempRootPath + storedFilename);
        File uploadFile = new File(uploadRootPath + storedFilename);

        // 상위 폴더(에: 2026/05//23)가 없으면 자동 생성
        File directory = uploadFile.getParentFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // 파일 덮어쓰기 복사 (원본은 절대 건드리지 않음)
        Files.copy(tempFile.toPath(), uploadFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // 2. (모든 DB 성공 시) Temp 폴더의 원본 찌거기 파일 삭제
    public void deleteTempFile(String storedFilename) {
        File file = new File(tempRootPath + storedFilename); // FileConfig 경로 사용!
        if (file.exists()) {
            boolean isDeleted = file.delete();
            if (!isDeleted) {
                // 삭제 실패 시 여기서 조용히 경고 로그만 띄워줍니다.
                System.err.println("Temp 파일 삭제 실패! 서버 확인 필요: " + file.getAbsolutePath());
            }
        }
    }

    // 3. (에러 롤백 시) Upload 폴더에 잘못 복사된 파일 파기
    public void deleteUploadFile(String storedFilename) {
        File file = new File(uploadRootPath + storedFilename); // FileConfig 경로 사용!
        if (file.exists()) {
            boolean isDeleted = file.delete();
            if (!isDeleted) {
                System.err.println("Upload 롤백 파일 삭제 실패! 서버 확인 필요: " + file.getAbsolutePath());
            }
        }
    }
}