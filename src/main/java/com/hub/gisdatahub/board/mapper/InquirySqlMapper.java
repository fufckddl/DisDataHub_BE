package com.hub.gisdatahub.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.board.dto.InquiryDetailDto;

@Mapper
public interface InquirySqlMapper {

    // 사용자 질문 목록 조회
    public List<InquiryDetailDto> findInquiryList();
}
