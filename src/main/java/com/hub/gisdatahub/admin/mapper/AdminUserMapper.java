package com.hub.gisdatahub.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.admin.dto.UserManagementLogDto;
import com.hub.gisdatahub.admin.dto.UserManagementTypeDto;
import com.hub.gisdatahub.user.domain.User;

@Mapper
public interface AdminUserMapper {
    // 사용자 목록
    public List<User> findUserAll();

    // 사용자 디테일
    public User findUserById(Integer id);

    // 사용자 제제
    public List<UserManagementTypeDto> findUserManagementTypeList();
    public void updateUserStatus(@Param("id") Integer id, @Param("status") String status);
    
    // 사용자 제제 로그
    public void insertUserManagementLog(UserManagementLogDto userManagementLogDto); 
}
