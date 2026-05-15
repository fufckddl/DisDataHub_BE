package com.hub.gisdatahub.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserLoginRequestDto {
    private String accountName;
    private String password;
}
