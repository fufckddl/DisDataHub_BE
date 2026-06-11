package com.hub.gisdatahub.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserLoginResponseDto {
    private Boolean result;
    private String token;
    private String username;
    private Integer userId;
    private String role;

     // 생성자: 서비스에서 LoginResponse("토큰값") 으로 바로 생성
     public UserLoginResponseDto(Boolean result, String token, String username, int userId) {
        this(result, token, username, userId, null);
    }

    public UserLoginResponseDto(Boolean result, String token, String username, int userId, String role) {
        this.result = result;
        this.token = token;
        this.username = username;
        this.userId = userId;
        this.role = role;
    }
}
