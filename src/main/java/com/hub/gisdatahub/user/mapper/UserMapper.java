package com.hub.gisdatahub.user.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.user.domain.User;

@Mapper
public interface UserMapper {
    // 사용자 아이디로 유저 객체 받기
    public User findByAccountName(String accountName); 
    // id로 유저 조회
    public User findById(Integer id);
    // 회원가입
    public void joinUser(User user);
}
