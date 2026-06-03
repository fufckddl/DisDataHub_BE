package com.hub.gisdatahub.board.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hub.gisdatahub.board.dto.InquiryCreateRequestDto;
import com.hub.gisdatahub.board.dto.InquiryDetailDto;
import com.hub.gisdatahub.board.service.InquiryService;

@RestController
@RequestMapping("/api/board/inquiries")
public class InquiryController {

    @Autowired
    public InquiryService inquiryService;

    @GetMapping("findInquiryList")
    public Map<String, Object> findInquiryList() {
        Map<String, Object> response = new HashMap<>();

        List<InquiryDetailDto> inquiryList = inquiryService.getInquiryList();

        response.put("inquiryList", inquiryList);
        response.put("result", "success");

        return response;
    }

    @PostMapping("createInquiry")
    public Map<String, Object> createInquiry(@RequestBody InquiryCreateRequestDto requestDto) {
        Map<String, Object> response = new HashMap<>();

        inquiryService.createInquiryPost(requestDto);

        response.put("result", "success");

        return response;
    }
}
