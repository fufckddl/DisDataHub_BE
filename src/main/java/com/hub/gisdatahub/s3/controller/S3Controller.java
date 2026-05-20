package com.hub.gisdatahub.s3.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hub.gisdatahub.s3.dto.S3DownloadResult;
import com.hub.gisdatahub.s3.dto.S3ObjectResult;
import com.hub.gisdatahub.s3.service.S3FileService;

@RestController
@RequestMapping("/api/s3")
public class S3Controller {

    private final S3FileService s3FileService;

    public S3Controller(S3FileService s3FileService) {
        this.s3FileService = s3FileService;
    }

    @PostMapping(value = "/upload-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<S3ObjectResult> uploadToUploadFiles(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(s3FileService.uploadToUploadFiles(file));
    }

    @PostMapping(value = "/temp-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<S3ObjectResult> uploadToTempFiles(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(s3FileService.uploadToTempFiles(file));
    }

    @PostMapping("/upload-files/local")
    public ResponseEntity<S3ObjectResult> uploadLocalFileToUploadFiles(@RequestParam("localFilePath") String localFilePath) {
        return ResponseEntity.ok(s3FileService.uploadLocalFileToUploadFiles(localFilePath));
    }

    @PostMapping("/temp-files/local")
    public ResponseEntity<S3ObjectResult> uploadLocalFileToTempFiles(@RequestParam("localFilePath") String localFilePath) {
        return ResponseEntity.ok(s3FileService.uploadLocalFileToTempFiles(localFilePath));
    }

    @PostMapping("/temp-files/copy-to-upload-files")
    public ResponseEntity<S3ObjectResult> copyTempFileToUploadFiles(@RequestParam("storedFilename") String storedFilename) {
        return ResponseEntity.ok(s3FileService.copyTempFileToUploadFiles(storedFilename));
    }

    @DeleteMapping("/files")
    public ResponseEntity<Void> deleteFile(
            @RequestParam("filePath") String filePath,
            @RequestParam("storedFilename") String storedFilename) {
        s3FileService.deleteFile(filePath, storedFilename);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/files/download")
    public ResponseEntity<ByteArrayResource> downloadFile(
            @RequestParam("filePath") String filePath,
            @RequestParam("storedFilename") String storedFilename,
            @RequestParam("originalFilename") String originalFilename) {
        S3DownloadResult result = s3FileService.downloadFile(filePath, storedFilename, originalFilename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .body(result.resource());
    }
}
