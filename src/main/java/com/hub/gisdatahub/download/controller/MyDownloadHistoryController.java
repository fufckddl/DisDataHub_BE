package com.hub.gisdatahub.download.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.download.service.MyDownloadHistoryService;

@RestController
@RequestMapping("/api/download/my-history")
public class MyDownloadHistoryController {

    private final MyDownloadHistoryService myDownloadHistoryService;

    public MyDownloadHistoryController(MyDownloadHistoryService myDownloadHistoryService) {
        this.myDownloadHistoryService = myDownloadHistoryService;
    }

    @GetMapping
    public Map<String, Object> getMyDownloadHistory(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "period", defaultValue = "all") String period,
            @RequestParam(name = "fileFormat", required = false) String fileFormat,
            @RequestParam(name = "sort", defaultValue = "latest") String sort,
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            Authentication authentication
    ) {
        Integer userId = resolveAuthenticatedUserId(authentication);
        return myDownloadHistoryService.getMyDownloadHistory(
                userId,
                keyword,
                period,
                fileFormat,
                sort,
                page,
                size
        );
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
}
