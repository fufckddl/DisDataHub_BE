package com.hub.gisdatahub.user.dto;

import com.hub.gisdatahub.user.type.UserRole;

import lombok.Data;

@Data
public class UserJoinRequest {
    private String accountName; // 아이디
    private String password; // 비밀번호
    private String username; // 이름
    private UserRole role; // 역할
    private String gender; // 성별
    private String email; // 이메일
    private String organization; // 소속 기관
    private String department; // 부서
    //private String status; // 상태 기본 (activate)
}
