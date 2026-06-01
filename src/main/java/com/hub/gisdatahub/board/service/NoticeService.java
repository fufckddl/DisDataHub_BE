package com.hub.gisdatahub.board.service;

import java.util.List;

import com.hub.gisdatahub.board.dto.BoardPostDto;

public interface NoticeService {
    public List<BoardPostDto> getNoticeList();

    public BoardPostDto getNoticeDetailData(Long postId);

    public void createNoticePost(BoardPostDto boardPostDto);

    public List<BoardPostDto> getAdminNoticeList();

    public BoardPostDto getAdminNoticeDetailData(Long postId);

    public void updateNoticePost(Long postId, BoardPostDto boardPostDto);

    public void deleteNoticePost(Long postId);
}
