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
}
