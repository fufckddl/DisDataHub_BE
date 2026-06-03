package com.hub.gisdatahub.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.board.dto.BoardPostDto;
import com.hub.gisdatahub.board.dto.BoardReplyDto;
import com.hub.gisdatahub.board.dto.InquiryDetailDto;

@Mapper
public interface InquirySqlMapper {
    // 사용자 질문 목록 조회
    public List<InquiryDetailDto> findInquiryList();
    // 질문게시판 기본정보 저장 커리
    public void insertInquiryPost(BoardPostDto boardPostDto);
    // 질문게시판 상세정보 저장 커리
    public void insertInquiryDetail(InquiryDetailDto inquiryDetailDto);
    // 문의게시판 디테일 페이지
    public InquiryDetailDto findInquiryDetail(Long postId);
    // 문의게시판 카운트
    public int increaseInquiryViewCount(Long postId);
    

    // 관리자 질문게시판 목록 조회
    public List<InquiryDetailDto> findAdminInquiryList();
    // 관리자 질문게시글 디테일 페이지
    public InquiryDetailDto findAdminInquiryDetail(Long postId);
    // 관리자 답변기능
    public void insertInquiryAnswer(BoardReplyDto boardReplyDto);
    // 관리자 상태 변경
    public int updateInquiryStatus(@Param("postId") Long postId, @Param("inquiryStatusCode") String inquiryStatusCode);
}
