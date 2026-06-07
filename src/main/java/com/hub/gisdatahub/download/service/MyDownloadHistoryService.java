package com.hub.gisdatahub.download.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hub.gisdatahub.download.dto.DownloadDatasetListItemDto;
import com.hub.gisdatahub.download.mapper.MyDownloadHistoryMapper;

@Service
public class MyDownloadHistoryService {

    private final MyDownloadHistoryMapper myDownloadHistoryMapper;

    public MyDownloadHistoryService(MyDownloadHistoryMapper myDownloadHistoryMapper) {
        this.myDownloadHistoryMapper = myDownloadHistoryMapper;
    }

    public Map<String, Object> getMyDownloadHistory(
            Integer userId,
            String keyword,
            String period,
            String fileFormat,
            String sort,
            Integer page,
            Integer size
    ) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요한 페이지입니다.");
        }

        String normalizedKeyword = normalizeFilter(keyword);
        String normalizedFileFormat = normalizeFileFormat(fileFormat);
        String normalizedSort = normalizeSort(sort);
        LocalDateTime periodStart = resolvePeriodStart(period);
        int safeSize = clamp(size == null ? 10 : size, 1, 50);
        int safePage = Math.max(page == null ? 1 : page, 1);
        int offset = (safePage - 1) * safeSize;

        List<Map<String, Object>> historyList = myDownloadHistoryMapper.findMyDownloadHistory(
                userId,
                normalizedKeyword,
                periodStart,
                normalizedFileFormat,
                normalizedSort,
                safeSize,
                offset
        );
        Integer historyTotalCount = myDownloadHistoryMapper.countMyDownloadHistory(
                userId,
                normalizedKeyword,
                periodStart,
                normalizedFileFormat
        );
        List<DownloadDatasetListItemDto> favoriteList = myDownloadHistoryMapper.findMyFavoriteDatasets(
                userId,
                normalizedKeyword,
                periodStart,
                normalizedFileFormat,
                normalizedSort,
                safeSize,
                offset
        );
        Integer favoriteTotalCount = myDownloadHistoryMapper.countMyFavoriteDatasets(
                userId,
                normalizedKeyword,
                periodStart,
                normalizedFileFormat
        );

        LocalDateTime recent30DaysStart = LocalDateTime.now().minusDays(30);
        Map<String, Object> response = new HashMap<>();
        response.put("historyList", historyList);
        response.put("favoriteList", favoriteList);
        response.put("recentList", myDownloadHistoryMapper.findMyRecentViewedDatasets(userId, 6));
        response.put("formatStats", myDownloadHistoryMapper.findMyDownloadFormatStats(userId, recent30DaysStart));
        response.put("summary", buildSummary(userId, recent30DaysStart));
        response.put("page", safePage);
        response.put("size", safeSize);
        response.put("historyTotalCount", historyTotalCount == null ? 0 : historyTotalCount);
        response.put("favoriteTotalCount", favoriteTotalCount == null ? 0 : favoriteTotalCount);

        return response;
    }

    private Map<String, Object> buildSummary(Integer userId, LocalDateTime recent30DaysStart) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalDownloadCount", defaultZero(myDownloadHistoryMapper.countMyDownloads(userId, null)));
        summary.put("recentDownloadCount", defaultZero(myDownloadHistoryMapper.countMyDownloads(userId, recent30DaysStart)));
        summary.put("favoriteCount", defaultZero(myDownloadHistoryMapper.countMyFavoriteDatasets(userId, null, null, null)));
        summary.put("latestDownload", myDownloadHistoryMapper.findMyLatestDownload(userId));
        summary.put("mostUsedFormat", myDownloadHistoryMapper.findMyMostUsedFormat(userId, recent30DaysStart));
        return summary;
    }

    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime resolvePeriodStart(String period) {
        String normalized = normalizeFilter(period);
        if (normalized == null || "all".equalsIgnoreCase(normalized)) {
            return null;
        }

        return switch (normalized) {
            case "30" -> LocalDateTime.now().minusDays(30);
            case "90" -> LocalDateTime.now().minusDays(90);
            default -> null;
        };
    }

    private String normalizeFileFormat(String fileFormat) {
        String normalized = normalizeFilter(fileFormat);
        if (normalized == null || "전체".equals(normalized)) {
            return null;
        }

        return normalized.toUpperCase();
    }

    private String normalizeSort(String sort) {
        String normalized = normalizeFilter(sort);
        if (normalized == null) {
            return "latest";
        }

        return switch (normalized) {
            case "title" -> "title";
            default -> "latest";
        };
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
