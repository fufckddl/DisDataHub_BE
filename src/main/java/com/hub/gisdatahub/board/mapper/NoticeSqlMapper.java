package com.hub.gisdatahub.board.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.hub.gisdatahub.board.dto.BoardPostDto;

@Mapper
public interface NoticeSqlMapper {
    // 공지사항 게시글 목록조회
    public List<BoardPostDto> findByNoticeList();

    // 공지사랑 상세페이지
    public BoardPostDto findByNoticeDetailData(Long postID);
}

