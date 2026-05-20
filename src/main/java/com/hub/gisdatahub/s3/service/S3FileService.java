package com.hub.gisdatahub.s3.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.hub.gisdatahub.s3.dto.S3DownloadResult;
import com.hub.gisdatahub.s3.dto.S3ObjectResult;
import com.hub.gisdatahub.s3.type.S3PathType;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class S3FileService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final S3Client s3Client;
    private final String bucket;

    public S3FileService(S3Client s3Client, @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public S3ObjectResult uploadToUploadFiles(MultipartFile file) {
        return uploadMultipartFile(file, S3PathType.UPLOAD_FILES);
    }

    public S3ObjectResult uploadToTempFiles(MultipartFile file) {
        return uploadMultipartFile(file, S3PathType.TEMP_FILES);
    }

    public S3ObjectResult uploadLocalFileToUploadFiles(String localFilePath) {
        return uploadLocalFile(localFilePath, S3PathType.UPLOAD_FILES);
    }

    public S3ObjectResult uploadLocalFileToTempFiles(String localFilePath) {
        return uploadLocalFile(localFilePath, S3PathType.TEMP_FILES);
    }

    public S3ObjectResult copyTempFileToUploadFiles(String storedFilename) {
        String normalizedStoredFilename = normalizeStoredFilename(storedFilename);
        String sourceKey = buildObjectKey(S3PathType.TEMP_FILES.getRootPath(), normalizedStoredFilename);
        String destinationKey = buildObjectKey(S3PathType.UPLOAD_FILES.getRootPath(), normalizedStoredFilename);

        try {
            CopyObjectResponse response = s3Client.copyObject(
                    CopyObjectRequest.builder()
                            .sourceBucket(bucket)
                            .sourceKey(sourceKey)
                            .destinationBucket(bucket)
                            .destinationKey(destinationKey)
                            .build());

            String eTag = response.copyObjectResult() == null ? null : response.copyObjectResult().eTag();
            return new S3ObjectResult(
                    bucket,
                    S3PathType.UPLOAD_FILES.getRootPath(),
                    normalizedStoredFilename,
                    extractFileName(destinationKey),
                    null,
                    eTag,
                    null,
                    "copied");
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "복사할 원본 파일을 찾을 수 없습니다.", e);
        } catch (S3Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "S3 파일 복사에 실패했습니다.", e);
        }
    }

    public void deleteFile(String filePath, String storedFilename) {
        String objectKey = buildObjectKey(filePath, storedFilename);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "S3 파일 삭제에 실패했습니다.", e);
        }
    }

    public S3DownloadResult downloadFile(String filePath, String storedFilename, String originalFilename) {
        String objectKey = buildObjectKey(filePath, storedFilename);
        String downloadFilename = resolveDownloadFilename(originalFilename, objectKey);

        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build());

            GetObjectResponse metadata = response.response();
            return new S3DownloadResult(
                    bucket,
                    objectKey,
                    downloadFilename,
                    metadata.contentType(),
                    new ByteArrayResource(response.asByteArray()));
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다운로드할 파일을 찾을 수 없습니다.", e);
        } catch (S3Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "S3 파일 다운로드에 실패했습니다.", e);
        }
    }

    public String buildObjectKey(String filePath, String storedFilename) {
        S3PathType pathType = S3PathType.fromFilePath(filePath);
        String normalizedStoredFilename = normalizeStoredFilename(storedFilename);
        return pathType.getRootPath() + "/" + normalizedStoredFilename;
    }

    public String createStoredFilename(String originalFileName) {
        String extension = extractExtension(originalFileName);
        LocalDate today = LocalDate.now(DEFAULT_ZONE_ID);
        String uuid = UUID.randomUUID().toString();
        String datePath = today.getYear()
                + "/" + String.format("%02d", today.getMonthValue())
                + "/" + String.format("%02d", today.getDayOfMonth());
        String savedFileName = extension.isBlank() ? uuid : uuid + "." + extension;
        return datePath + "/" + savedFileName;
    }

    private S3ObjectResult uploadMultipartFile(MultipartFile file, S3PathType pathType) {
        validateMultipartFile(file);

        String storedFilename = createStoredFilename(file.getOriginalFilename());
        String objectKey = buildObjectKey(pathType.getRootPath(), storedFilename);
        String contentType = resolveContentType(file.getContentType());

        try {
            PutObjectResponse response = s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));

            return new S3ObjectResult(
                    bucket,
                    pathType.getRootPath(),
                    storedFilename,
                    file.getOriginalFilename(),
                    contentType,
                    response.eTag(),
                    file.getSize(),
                    "uploaded");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "업로드 파일을 읽는 중 오류가 발생했습니다.", e);
        } catch (S3Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "S3 파일 업로드에 실패했습니다.", e);
        }
    }

    private S3ObjectResult uploadLocalFile(String localFilePath, S3PathType pathType) {
        Path path = resolveLocalPath(localFilePath);
        String storedFilename = createStoredFilename(path.getFileName().toString());
        String objectKey = buildObjectKey(pathType.getRootPath(), storedFilename);

        try {
            String contentType = resolveContentType(Files.probeContentType(path));
            PutObjectResponse response = s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromFile(path));

            return new S3ObjectResult(
                    bucket,
                    pathType.getRootPath(),
                    storedFilename,
                    path.getFileName().toString(),
                    contentType,
                    response.eTag(),
                    Files.size(path),
                    "uploaded");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "로컬 파일을 읽는 중 오류가 발생했습니다.", e);
        } catch (S3Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "S3 파일 업로드에 실패했습니다.", e);
        }
    }

    private Path resolveLocalPath(String localFilePath) {
        if (localFilePath == null || localFilePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "localFilePath 값이 필요합니다.");
        }

        Path path = Path.of(localFilePath.trim());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지정한 로컬 파일을 찾을 수 없습니다.");
        }

        return path;
    }

    private void validateMultipartFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드할 파일이 비어 있습니다.");
        }
    }

    private String extractExtension(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "";
        }

        String fileName = originalFileName.trim();
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(extensionIndex + 1);
    }

    private String extractFileName(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        return slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
    }

    private String resolveDownloadFilename(String originalFilename, String objectKey) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return extractFileName(objectKey);
        }

        return originalFilename.trim();
    }

    private String normalizeStoredFilename(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storedFilename 값이 필요합니다.");
        }

        String normalizedStoredFilename = storedFilename.trim().replace("\\", "/");
        while (normalizedStoredFilename.startsWith("/")) {
            normalizedStoredFilename = normalizedStoredFilename.substring(1);
        }

        if (normalizedStoredFilename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storedFilename 값이 올바르지 않습니다.");
        }

        if (normalizedStoredFilename.startsWith(S3PathType.UPLOAD_FILES.getRootPath() + "/")
                || normalizedStoredFilename.startsWith(S3PathType.TEMP_FILES.getRootPath() + "/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storedFilename 에는 filePath 를 포함할 수 없습니다.");
        }

        return normalizedStoredFilename;
    }

    private String resolveContentType(String contentType) {
        return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
    }
}
