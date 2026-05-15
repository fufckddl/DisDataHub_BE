package com.hub.gisdatahub.exception;

import org.springframework.stereotype.Component;

@Component
public class UserException {
    public RuntimeException alredayUsedAccountException(){
        return new RuntimeException("이미 사용 중인 계정 이름입니다.");
    }
}
