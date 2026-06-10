package com.hub.gisdatahub.board.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        noticeSqlMapper.increaseNoticeViewCount(postId);

        return noticeSqlMapper.findByNoticeDetailData(postId);
    }

    @Override
    public Long getPreviousNoticePostId(Long postId) {
        return noticeSqlMapper.findPreviousNoticePostId(postId);
    }

    @Override
    public Long getNextNoticePostId(Long postId) {
        return noticeSqlMapper.findNextNoticePostId(postId);
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
    public Long getPreviousAdminNoticePostId(Long postId) {
        return noticeSqlMapper.findPreviousAdminNoticePostId(postId);
    }

    @Override
    public Long getNextAdminNoticePostId(Long postId) {
        return noticeSqlMapper.findNextAdminNoticePostId(postId);
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

        int updateCount = noticeSqlMapper.updateNoticePost(boardPostDto);

        if (updateCount == 0) {
            throw new RuntimeException("수정할 공지사항을 찾을 수 없습니다.");
        }
    }

    @Override
    public void deleteNoticePost(Long postId) {
        int deleteCount = noticeSqlMapper.deleteNoticePost(postId);

        if (deleteCount == 0) {
            throw new RuntimeException("삭제할 공지사항을 찾을 수 없습니다.");
        }
    }

    @Override
    @Transactional
    public Map<String, Object> restoreNoticePost(Long postId) {
        Map<String, Object> resultMap = new HashMap<>();

        int result = noticeSqlMapper.restoreNoticePost(postId);

        if (result > 0) {
            resultMap.put("result", "success");
            resultMap.put("message", "공지사항 삭제가 취소되었습니다.");
        } else {
            resultMap.put("result", "fail");
            resultMap.put("message", "삭제 취소할 공지사항이 없습니다.");
        }

        return resultMap;
    }
}