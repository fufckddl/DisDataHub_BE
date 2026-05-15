package com.hub.gisdatahub.user.domain;

import java.time.LocalDateTime;

import com.hub.gisdatahub.user.type.UserRole;
import com.hub.gisdatahub.user.type.UserStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    private Integer id; // pk
    private String accountName; // 아이디
    private String password; // 비밀번호
    private String username; // 이름
    private UserRole role; // 역할
    private String gender; // 성별
    private String email; // 이메일
    private String organization; // 소속 기관
    private String department; // 부서
    private UserStatus status; // 상태
    private LocalDateTime updated_at;
    private LocalDateTime deleted_at;
    private LocalDateTime created_at;
    
}
