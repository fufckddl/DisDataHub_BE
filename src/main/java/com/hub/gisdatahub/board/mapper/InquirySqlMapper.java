package com.hub.gisdatahub.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hub.gisdatahub.board.dto.BoardPostDto;
import com.hub.gisdatahub.board.dto.BoardReplyDto;
import com.hub.gisdatahub.board.dto.InquiryCreateRequestDto;
import com.hub.gisdatahub.board.dto.InquiryDetailDto;

@Mapper
public interface InquirySqlMapper {

    // 사용자 질문 목록 조회: 공개글 + 로그인 사용자의 비공개글
    public List<InquiryDetailDto> findInquiryList(
            @Param("loginUserId") Integer loginUserId
    );

    // 질문게시판 기본정보 저장
    public void insertInquiryPost(BoardPostDto boardPostDto);

    // 질문게시판 상세정보 저장
    public void insertInquiryDetail(InquiryDetailDto inquiryDetailDto);

    // 문의 상세 조회: 공개글 또는 작성자 본인 비공개글
    public InquiryDetailDto findInquiryDetail(
            @Param("postId") Long postId,
            @Param("loginUserId") Integer loginUserId
    );

    // 문의 조회수 증가
    public int increaseInquiryViewCount(
            @Param("postId") Long postId,
            @Param("loginUserId") Integer loginUserId
    );

    // 사용자 문의 이전글
    public Long findPreviousInquiryPostId(
            @Param("postId") Long postId,
            @Param("loginUserId") Integer loginUserId
    );

    // 사용자 문의 다음글
    public Long findNextInquiryPostId(
            @Param("postId") Long postId,
            @Param("loginUserId") Integer loginUserId
    );

    // 관리자 질문게시판 목록 조회
    public List<InquiryDetailDto> findAdminInquiryList();

    // 관리자 질문게시글 상세 조회
    public InquiryDetailDto findAdminInquiryDetail(Long postId);

    // 관리자 문의 이전글
    public Long findPreviousAdminInquiryPostId(Long postId);

    // 관리자 문의 다음글
    public Long findNextAdminInquiryPostId(Long postId);

    // 관리자 답변 저장
    public void insertInquiryAnswer(BoardReplyDto boardReplyDto);

    // 관리자 상태 변경
    public int updateInquiryStatus(
            @Param("postId") Long postId,
            @Param("inquiryStatusCode") String inquiryStatusCode
    );

    // 사용자 본인 문의 공통 게시글 수정
    public int updateMyInquiryPost(InquiryCreateRequestDto requestDto);

    // 사용자 본인 문의 상세 정보 수정
    public int updateMyInquiryDetail(InquiryCreateRequestDto requestDto);

    // 사용자 본인 문의 삭제
    public int deleteMyInquiryPost(
            @Param("postId") Long postId,
            @Param("userId") Integer userId
    );
}