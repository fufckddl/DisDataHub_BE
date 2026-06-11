package com.hub.gisdatahub.board.service;

import java.util.List;

import com.hub.gisdatahub.board.dto.AdminInquiryAnswerRequestDto;
import com.hub.gisdatahub.board.dto.InquiryCreateRequestDto;
import com.hub.gisdatahub.board.dto.InquiryDetailDto;

public interface InquiryService {

    // 질문 게시판 목록 조회
    public List<InquiryDetailDto> getInquiryList(Integer loginUserId);

    // 질문 게시판 작성
    public void createInquiryPost(InquiryCreateRequestDto requestDto);

    // 질문 게시판 상세 조회
    public InquiryDetailDto getInquiryDetail(Long postId, Integer loginUserId);

    // 사용자 질문게시판 이전글
    public Long getPreviousInquiryPostId(Long postId, Integer loginUserId);

    // 사용자 질문게시판 다음글
    public Long getNextInquiryPostId(Long postId, Integer loginUserId);

    // 관리자 질문게시판 목록 조회
    public List<InquiryDetailDto> getAdminInquiryList();

    // 관리자 질문게시판 상세 조회
    public InquiryDetailDto getAdminInquiryDetail(Long postId);

    // 관리자 질문게시판 이전글
    public Long getPreviousAdminInquiryPostId(Long postId);

    // 관리자 질문게시판 다음글
    public Long getNextAdminInquiryPostId(Long postId);

    // 관리자 문의 답변 저장 및 상태 변경
    public void saveAdminInquiryAnswer(Long postId, AdminInquiryAnswerRequestDto requestDto);

    // 사용자 본인 문의 수정
    public void updateMyInquiry(
            Long postId,
            Integer loginUserId,
            InquiryCreateRequestDto requestDto
    );

    // 사용자 본인 문의 삭제
    public void deleteMyInquiry(Long postId, Integer loginUserId);
}