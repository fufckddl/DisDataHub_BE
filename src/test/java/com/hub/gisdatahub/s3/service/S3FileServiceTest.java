package com.hub.gisdatahub.s3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.hub.gisdatahub.s3.dto.S3DownloadResult;
import com.hub.gisdatahub.s3.dto.S3ObjectResult;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.CopyObjectResult;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3FileServiceTest {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String BUCKET = "test-bucket";

    @Mock
    private S3Client s3Client;

    @Captor
    private ArgumentCaptor<PutObjectRequest> putObjectRequestCaptor;

    @Captor
    private ArgumentCaptor<CopyObjectRequest> copyObjectRequestCaptor;

    @Captor
    private ArgumentCaptor<DeleteObjectRequest> deleteObjectRequestCaptor;

    private S3FileService s3FileService;

    @BeforeEach
    void setUp() {
        s3FileService = new S3FileService(s3Client, BUCKET);
    }

    @Test
    void uploadToTempFilesCreatesDateBasedStoredFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-1").build());

        S3ObjectResult result = s3FileService.uploadToTempFiles(file);

        verify(s3Client).putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));

        String expectedPrefix = LocalDate.now(SEOUL_ZONE_ID).format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/";
        assertThat(result.bucket()).isEqualTo(BUCKET);
        assertThat(result.filePath()).isEqualTo("tempFiles");
        assertThat(result.originalFilename()).isEqualTo("test.txt");
        assertThat(result.contentType()).isEqualTo("text/plain");
        assertThat(result.size()).isEqualTo(5L);
        assertThat(result.action()).isEqualTo("uploaded");
        assertThat(result.storedFilename()).startsWith(expectedPrefix);
        assertThat(result.storedFilename()).matches(expectedPrefix + "[0-9a-f\\-]{36}\\.txt");

        PutObjectRequest request = putObjectRequestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo("tempFiles/" + result.storedFilename());
        assertThat(request.contentType()).isEqualTo("text/plain");
    }

    @Test
    void uploadLocalFileToUploadFilesUsesGeneratedStoredFilename(@TempDir Path tempDir) throws Exception {
        Path filePath = tempDir.resolve("report.csv");
        Files.writeString(filePath, "id,name\n1,test", StandardCharsets.UTF_8);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-2").build());

        S3ObjectResult result = s3FileService.uploadLocalFileToUploadFiles(filePath.toString());

        verify(s3Client).putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));

        String expectedPrefix = LocalDate.now(SEOUL_ZONE_ID).format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/";
        assertThat(result.filePath()).isEqualTo("uploadFiles");
        assertThat(result.storedFilename()).startsWith(expectedPrefix);
        assertThat(result.storedFilename()).matches(expectedPrefix + "[0-9a-f\\-]{36}\\.csv");
        assertThat(result.originalFilename()).isEqualTo("report.csv");
        assertThat(result.action()).isEqualTo("uploaded");

        PutObjectRequest request = putObjectRequestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo("uploadFiles/" + result.storedFilename());
    }

    @Test
    void copyTempFileToUploadFilesPreservesStoredFilename() {
        String storedFilename = "2026/05/20/123e4567-e89b-12d3-a456-426614174000.png";

        when(s3Client.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(CopyObjectResponse.builder()
                        .copyObjectResult(CopyObjectResult.builder().eTag("etag-3").build())
                        .build());

        S3ObjectResult result = s3FileService.copyTempFileToUploadFiles(storedFilename);

        verify(s3Client).copyObject(copyObjectRequestCaptor.capture());

        assertThat(result.filePath()).isEqualTo("uploadFiles");
        assertThat(result.storedFilename()).isEqualTo(storedFilename);
        assertThat(result.originalFilename()).isEqualTo("123e4567-e89b-12d3-a456-426614174000.png");
        assertThat(result.action()).isEqualTo("copied");

        CopyObjectRequest request = copyObjectRequestCaptor.getValue();
        assertThat(request.sourceBucket()).isEqualTo(BUCKET);
        assertThat(request.sourceKey()).isEqualTo("tempFiles/" + storedFilename);
        assertThat(request.destinationBucket()).isEqualTo(BUCKET);
        assertThat(request.destinationKey()).isEqualTo("uploadFiles/" + storedFilename);
    }

    @Test
    void copyTempFileToUploadFilesRejectsStoredFilenameIncludingFilePath() {
        assertThatThrownBy(() -> s3FileService.copyTempFileToUploadFiles("uploadFiles/2026/05/20/test.txt"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void deleteFileUsesFilePathAndStoredFilename() {
        s3FileService.deleteFile("uploadFiles", "2026/05/20/test.txt");

        verify(s3Client).deleteObject(deleteObjectRequestCaptor.capture());
        DeleteObjectRequest request = deleteObjectRequestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo("uploadFiles/2026/05/20/test.txt");
    }

    @Test
    void downloadFileReturnsResourceWithOriginalFilename() throws Exception {
        byte[] content = "download-content".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse response = GetObjectResponse.builder()
                .contentType("text/plain")
                .build();

        when(s3Client.getObjectAsBytes(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(response, content));

        S3DownloadResult result = s3FileService.downloadFile("uploadFiles", "2026/05/20/test.txt", "originalFile.txt");

        assertThat(result.bucket()).isEqualTo(BUCKET);
        assertThat(result.key()).isEqualTo("uploadFiles/2026/05/20/test.txt");
        assertThat(result.fileName()).isEqualTo("originalFile.txt");
        assertThat(result.contentType()).isEqualTo("text/plain");
        assertThat(result.resource().getByteArray()).isEqualTo(content);
    }

    @Test
    void buildObjectKeyRejectsInvalidFilePath() {
        assertThatThrownBy(() -> s3FileService.buildObjectKey("invalidPath", "2026/05/20/test.txt"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }
}
