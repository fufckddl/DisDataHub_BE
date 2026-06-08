package com.hub.gisdatahub.download.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.download.dto.DownloadDatasetListItemDto;

@Mapper
public interface MyDownloadHistoryMapper {

    List<Map<String, Object>> findMyDownloadHistory(
            @Param("userId") Integer userId,
            @Param("keyword") String keyword,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("fileFormat") String fileFormat,
            @Param("sort") String sort,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset
    );

    Integer countMyDownloadHistory(
            @Param("userId") Integer userId,
            @Param("keyword") String keyword,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("fileFormat") String fileFormat
    );

    List<DownloadDatasetListItemDto> findMyFavoriteDatasets(
            @Param("userId") Integer userId,
            @Param("keyword") String keyword,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("fileFormat") String fileFormat,
            @Param("sort") String sort,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset
    );

    Integer countMyFavoriteDatasets(
            @Param("userId") Integer userId,
            @Param("keyword") String keyword,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("fileFormat") String fileFormat
    );

    List<DownloadDatasetListItemDto> findMyRecentViewedDatasets(
            @Param("userId") Integer userId,
            @Param("limit") Integer limit
    );

    List<Map<String, Object>> findMyDownloadFormatStats(
            @Param("userId") Integer userId,
            @Param("periodStart") LocalDateTime periodStart
    );

    Integer countMyDownloads(
            @Param("userId") Integer userId,
            @Param("periodStart") LocalDateTime periodStart
    );

    Map<String, Object> findMyLatestDownload(@Param("userId") Integer userId);

    String findMyMostUsedFormat(
            @Param("userId") Integer userId,
            @Param("periodStart") LocalDateTime periodStart
    );
}
