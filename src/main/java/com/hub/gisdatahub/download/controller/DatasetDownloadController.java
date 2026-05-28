package com.hub.gisdatahub.download.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.download.dto.DatasetDownloadPageDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetListItemDto;
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
}
