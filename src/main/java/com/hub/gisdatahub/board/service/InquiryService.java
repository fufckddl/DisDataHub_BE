package com.hub.gisdatahub.board.service;

import java.util.List;

import com.hub.gisdatahub.board.dto.BoardPostDto;
import com.hub.gisdatahub.board.dto.InquiryCreateRequestDto;
import com.hub.gisdatahub.board.dto.InquiryDetailDto;

public interface InquiryService {
    // 질문 게시판 목록 조회코드
    public List<InquiryDetailDto> getInquiryList();
    // 질문 게시판 기본정보 저장 코드
    public void createInquiryPost(InquiryCreateRequestDto requestDto);
    // 질문 게시판 상세페이지 불러오는 코드
    public InquiryDetailDto getInquiryDetail(Long postId);

    // 관리자 질문게시판 목록 조회코드
    public List<InquiryDetailDto> getAdminInquiryList();
}
