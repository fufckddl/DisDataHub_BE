package com.hub.gisdatahub.board.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hub.gisdatahub.board.dto.BoardPostDto;
import com.hub.gisdatahub.board.mapper.NoticeSqlMapper;

@Service
public class NoticeServiceImpl implements NoticeService {

    @Autowired
    public NoticeSqlMapper noticeSqlMapper;

    @Override
    public List<BoardPostDto> getNoticeList() {
        return noticeSqlMapper.findByNoticeList();
    }

    @Override
    public BoardPostDto getNoticeDetailData(Long postId) {
        return noticeSqlMapper.findByNoticeDetailData(postId);
    }

    @Override
    public void createNoticePost(BoardPostDto boardPostDto) {
        if (boardPostDto.getVisibilityStatus() == null) {
            boardPostDto.setVisibilityStatus("PUBLIC");
        }

        if (boardPostDto.getPinnedYn() == null) {
            boardPostDto.setPinnedYn("N");
        }
        
        noticeSqlMapper.insertNotice(boardPostDto);
    }

    @Override
    public List<BoardPostDto> getAdminNoticeList() {
        return noticeSqlMapper.findAdminNoticeList();
    }

    @Override
    public BoardPostDto getAdminNoticeDetailData(Long postId) {
        return noticeSqlMapper.findByAdminDetailPage(postId);
    }

    @Override
    public void updateNoticePost(Long postId, BoardPostDto boardPostDto) {
        boardPostDto.setPostId(postId);

        if (boardPostDto.getVisibilityStatus() == null) {
            boardPostDto.setVisibilityStatus("PUBLIC");
        }
        if (boardPostDto.getPinnedYn() == null) {
            boardPostDto.setPinnedYn("N");
        }

        noticeSqlMapper.updateNoticePost(boardPostDto);
    }
}
