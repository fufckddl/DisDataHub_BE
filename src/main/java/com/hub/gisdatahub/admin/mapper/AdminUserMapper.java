package com.hub.gisdatahub.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.admin.dto.UserManagementLogDto;
import com.hub.gisdatahub.admin.dto.UserManagementTypeDto;

@Mapper
public interface AdminUserMapper {

    // 제제 유형 목록 조회
    List<UserManagementTypeDto> selectUserManagementTypeList();

    // 제제 로그 등록
    Integer insertUserManagementLog(UserManagementLogDto userManagementLogDto);    
}
