package com.hub.gisdatahub.board.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.board.dto.BoardPostDto;
import com.hub.gisdatahub.board.service.NoticeService;

@RestController
@RequestMapping("/api/board/notices")
public class NoticeController {

    @Autowired
    public NoticeService noticeService;

    @GetMapping("findNoticeList")
    public Map<String, Object> findNoticeList() {
        Map<String, Object> response = new HashMap<>();

        List<BoardPostDto> noticeList = noticeService.getNoticeList();

        response.put("noticeList", noticeList);
        response.put("result", "success");

        return response;
    }
}
