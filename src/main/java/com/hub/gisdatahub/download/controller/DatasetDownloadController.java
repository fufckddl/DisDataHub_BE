package com.hub.gisdatahub.download.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.download.dto.DatasetDownloadPageDto;
import com.hub.gisdatahub.download.dto.DatasetFavoriteResponseDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetListItemDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetSearchResponseDto;
import com.hub.gisdatahub.download.dto.DownloadExportResultDto;
import com.hub.gisdatahub.download.service.DatasetDownloadService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/download")
public class DatasetDownloadController {

    private final DatasetDownloadService datasetDownloadService;

    public DatasetDownloadController(DatasetDownloadService datasetDownloadService) {
        this.datasetDownloadService = datasetDownloadService;
    }

    @GetMapping("/datasets")
    public List<DownloadDatasetListItemDto> getApprovedDownloadDatasetList() {
        return datasetDownloadService.getApprovedDownloadDatasetList();
    }

    @GetMapping("/datasets/search")
    public DownloadDatasetSearchResponseDto getDownloadDatasetMainPage(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "provider", required = false) String provider,
            @RequestParam(name = "fileFormat", required = false) String fileFormat,
            @RequestParam(name = "categoryId", required = false) Integer categoryId,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "sort", defaultValue = "default") String sort,
            Authentication authentication
    ) {
        Integer userId = resolveAuthenticatedUserId(authentication);
        return datasetDownloadService.getDownloadDatasetMainPage(
                keyword,
                provider,
                fileFormat,
                categoryId,
                startDate,
                endDate,
                page,
                size,
                sort,
                userId
        );
    }

    @GetMapping("/datasets/{datasetId}")
    public DatasetDownloadPageDto getDatasetDownloadPage(
            @PathVariable("datasetId") Long datasetId, 
            Authentication authentication,
            HttpServletRequest request
        ) {
        Integer userId = resolveAuthenticatedUserId(authentication);
        String viewIp = extractClientIp(request);
        return datasetDownloadService.getDatasetDownloadPage(datasetId, userId, viewIp);
    }

    @PostMapping("/datasets/{datasetId}/favorite")
    public DatasetFavoriteResponseDto toggleDatasetFavorite(
            @PathVariable("datasetId") Long datasetId,
            Authentication authentication
    ) {
        Integer userId = resolveAuthenticatedUserId(authentication);
        return datasetDownloadService.toggleDatasetFavorite(datasetId, userId);
    }

    private Integer resolveAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof String principalValue)) {
            return null;
        }

        if ("anonymousUser".equals(principalValue)) {
            return null;
        }

        return Integer.parseInt(principalValue);
    }

    //  사용자 IP 갖고오기 위한 메서드
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }

        return request.getRemoteAddr();
    }

    @GetMapping("/datasets/{datasetId}/preview-geojson")
    public ResponseEntity<String> getDatasetPreviewGeoJson(
            @PathVariable("datasetId") Long datasetId,
            Authentication authentication) {

        Integer userId = resolveAuthenticatedUserId(authentication);
        String geoJson = datasetDownloadService.getDatasetPreviewGeoJson(datasetId, userId);

        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(geoJson);
    }

// 다운로드 파일 변환 과정
    @GetMapping("/datasets/{datasetId}/download")
    public ResponseEntity<ByteArrayResource> downloadDatasetByFormat(
        @PathVariable("datasetId") Long datasetId,
        @RequestParam("format") String format,
        Authentication authentication,
        HttpServletRequest request){


    Integer userId = resolveAuthenticatedUserId(authentication);
    String downloadIp = extractClientIp(request);

    DownloadExportResultDto result =
            datasetDownloadService.downloadDatasetByFormat(datasetId, format, userId, downloadIp);

    String contentDisposition = ContentDisposition.attachment()
            .filename(result.getFileName(), StandardCharsets.UTF_8)
            .build()
            .toString();

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            .contentType(MediaType.parseMediaType(result.getContentType()))
            .body(new ByteArrayResource(result.getBytes()));            

        }

}
