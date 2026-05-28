package com.hub.gisdatahub.board.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("{postId}")
    public Map<String, Object> findNoticeDetail(@PathVariable("postId") Long postId) {
        Map<String, Object> response = new HashMap<>();

        BoardPostDto noticeDetail = noticeService.getNoticeDetailData(postId);

        response.put("noticeDetail", noticeDetail);
        response.put("result", "success");

        return response;
    }

    @PostMapping("createNotice")
    public Map<String, Object> createNotice(@RequestBody BoardPostDto boardPostDto) {
        Map<String, Object> response = new HashMap<>();

        noticeService.createNoticePost(boardPostDto);

        response.put("result", "success");
        response.put("postId", boardPostDto.getPostId());

        return response;
    }

    @GetMapping("adminNoticeList")
    public Map<String, Object> adminNoticeList() {
        Map<String, Object> response = new HashMap<>();

        List<BoardPostDto> adminNotice = noticeService.getAdminNoticeList();

        response.put("result", "success");
        response.put("adminNotice", adminNotice);

        return response;
    }
}
