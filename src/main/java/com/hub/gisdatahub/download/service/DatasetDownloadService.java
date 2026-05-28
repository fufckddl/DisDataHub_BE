package com.hub.gisdatahub.download.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hub.gisdatahub.download.dto.DatasetDownloadPageDto;
import com.hub.gisdatahub.download.dto.DatasetStatDto;
import com.hub.gisdatahub.download.dto.DatasetViewLogDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetDetailDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetFileDto;
import com.hub.gisdatahub.download.dto.DownloadDatasetListItemDto;
import com.hub.gisdatahub.download.mapper.DatasetDownloadMapper;
import com.hub.gisdatahub.user.domain.User;
import com.hub.gisdatahub.user.mapper.UserMapper;

@Service
public class DatasetDownloadService {

    private final DatasetDownloadMapper datasetDownloadMapper;
    private final UserMapper userMapper;

    public DatasetDownloadService(DatasetDownloadMapper datasetDownloadMapper, UserMapper userMapper) {
        this.datasetDownloadMapper = datasetDownloadMapper;
        this.userMapper = userMapper;
    }


    // 메인페이지 데이터셋 목록
    public List<DownloadDatasetListItemDto> getApprovedDownloadDatasetList() {
        return datasetDownloadMapper.findApprovedDownloadDatasetList();
    }

    public DatasetDownloadPageDto getDatasetDownloadPage(Long datasetId, Integer userId, String viewIp) {
        DownloadDatasetDetailDto dataset = datasetDownloadMapper.findDatasetDetailById(datasetId);
        
        if (dataset == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "데이터셋을 찾을 수 없습니다.");
        }
        
        validateDatasetDetailAccess(dataset, userId);

        DatasetViewLogDto viewLogDto = new DatasetViewLogDto();
        viewLogDto.setDatasetId(datasetId);
        viewLogDto.setUserId(userId);
        viewLogDto.setViewIp(viewIp);
        
        recordDatasetView(viewLogDto);

        // datasetDownloadMapper.increaseViewCount(datasetId);
        DatasetStatDto stats = datasetDownloadMapper.findDatasetStatById(datasetId);
        if (stats == null) {
            stats = new DatasetStatDto();
            stats.setDatasetId(datasetId);
            stats.setViewCount(0);
            stats.setDownloadCount(0);
        }

        DownloadDatasetFileDto sourceFile = datasetDownloadMapper.findSourceFileByDatasetId(datasetId);

        DatasetDownloadPageDto response = new DatasetDownloadPageDto();
        response.setDataset(dataset);
        response.setSourceFile(sourceFile);
        response.setStats(stats);
        response.setAvailableFormats(List.of("CSV", "GeoJSON", "SHP", "GeoTIFF","KML"));

        return response;
    }

    private void validateDatasetDetailAccess(DownloadDatasetDetailDto dataset, Integer userId) {
        if (Boolean.TRUE.equals(dataset.getIsPublic())) {
            return;
        }

        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비공개 데이터셋은 로그인 후 접근할 수 있습니다.");
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "사용자 정보를 확인할 수 없습니다.");
        }

        if (!Objects.equals(normalize(user.getOrganization()), normalize(dataset.getProvider()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "동일 소속기관 사용자만 접근할 수 있습니다.");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    // 조회 로그 생성
    public void recordDatasetView(DatasetViewLogDto viewLogDto){
        boolean duplicate = isDuplicateView(
            viewLogDto.getDatasetId(),
            viewLogDto.getUserId(),
            viewLogDto.getViewIp()
        );

        //  이미 최근 조회한 기록이 있다면 로그 추가, 조회수 증가 하지 않음
        if (duplicate) {
            return;
        }

        datasetDownloadMapper.insertViewLog(viewLogDto);
        datasetDownloadMapper.increaseViewCount(viewLogDto.getDatasetId());
    }

    // 조회 로그, 조회수 중복 방지용 메서드
    private boolean isDuplicateView(Long datasetId, Integer userId, String viewIp){
        LocalDateTime fromTime = LocalDateTime.now().minusMinutes(5); // 중복 방지 범위 시간 지정 현재는 5분

        if (userId != null) {
            Integer recentCount = datasetDownloadMapper.countRecentViewByUser(datasetId, userId, fromTime);
            return recentCount != null && recentCount > 0;
        }
        
        Integer recentCount = datasetDownloadMapper.countRecentViewByIp(datasetId, viewIp, fromTime);
        return recentCount != null && recentCount > 0;
        
    }
}
