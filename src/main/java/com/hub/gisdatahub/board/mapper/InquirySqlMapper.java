package com.hub.gisdatahub.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.board.dto.BoardPostDto;
import com.hub.gisdatahub.board.dto.InquiryDetailDto;

@Mapper
public interface InquirySqlMapper {
    // 사용자 질문 목록 조회
    public List<InquiryDetailDto> findInquiryList();
    // 질문게시판 기본정보 저장 커리
    public void insertInquiryPost(BoardPostDto boardPostDto);
    // 질문게시판 상세정보 저장 커리
    public void insertInquiryDetail(InquiryDetailDto inquiryDetailDto);
}
