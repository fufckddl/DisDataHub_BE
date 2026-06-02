package com.hub.gisdatahub.download.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.download.dto.DatasetFeatureExportDto;
import com.hub.gisdatahub.download.dto.DatasetStatDto;
import com.hub.gisdatahub.download.dto.DatasetViewLogDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetDetailDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetFileDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetListItemDto;
import com.hub.gisdatahub.download.dto.DownloadLogDto;


@Mapper
public interface DatasetDownloadMapper {

    // 메인페이지 데이터셋 목록
    public List<DownloadDatasetListItemDto> findApprovedDownloadDatasetList();

    public DownloadDatasetDetailDto findDatasetDetailById(Long datasetId);

    public DownloadDatasetFileDto findSourceFileByDatasetId(Long datasetId);


    // 다운로드 수, 조회 수 찾기
    public Integer findDownloadCountById(Long datasetId);
    public Integer findViewCountById(Long datasetId);

    public DatasetStatDto findDatasetStatById(Long datasetId);


    // 조회수
    public void increaseViewCount(Long datasetId);
    

    // 조회 로그 생성
    public void insertViewLog(DatasetViewLogDto viewLogDto);

    
    // 조회 로그 중복 생성 방지용
    // 로그인한 사용자
    public Integer countRecentViewByUser(
        @Param("datasetId") Long datasetId,
        @Param("userId") Integer userId,
        @Param("fromTime") LocalDateTime fromTime
    );
    // 비로그인 사용자
    public Integer countRecentViewByIp(
        @Param("datasetId") Long datasetId,
        @Param("viewIp") String viewIp,
        @Param("fromTime") LocalDateTime fromTime
    );    
    

    // 데이터셋 업로드 한 사람의 소속기관 찾기
    public String findDatasetOwnerOrganization(Long datasetId);

    // 사용자의 소속기관 찾기
    public String findUserOrganization(Integer userId);


    //  공간 데이터 갖고오기
    public String findDatasetPreviewGeoJson(Long datasetId);
    
    
// 다운로드 파일 변환 과정
    public String findDatasetExportGeoJson(Long datasetId);
    
    public List<DatasetFeatureExportDto> findDatasetFeaturesForExport(Long datasetId);
    // 다운로드 수 증가
    public void increaseDownloadCount(Long datasetId);
    // 다운로드 로그 생성
    public void insertDownloadLog(DownloadLogDto downloadLogDto);    
}
