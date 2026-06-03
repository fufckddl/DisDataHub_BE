package com.hub.gisdatahub.board.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hub.gisdatahub.board.dto.BoardPostDto;
import com.hub.gisdatahub.board.dto.InquiryCreateRequestDto;
import com.hub.gisdatahub.board.dto.InquiryDetailDto;
import com.hub.gisdatahub.board.mapper.InquirySqlMapper;

@Service
public class InquiryServiceImpl implements InquiryService{
    
    @Autowired
    public InquirySqlMapper inquirySqlMapper;
    
    @Override
    public List<InquiryDetailDto> getInquiryList() {
        return inquirySqlMapper.findInquiryList();
    }

    @Transactional
    @Override
    public void createInquiryPost(InquiryCreateRequestDto requestDto) {
        BoardPostDto boardPostDto = new BoardPostDto();

        boardPostDto.setUserId(requestDto.getUserId());
        boardPostDto.setTitle(requestDto.getTitle());
        boardPostDto.setContent(requestDto.getContent());
        boardPostDto.setVisibilityStatus(requestDto.getVisibilityStatus());
        
        if (boardPostDto.getVisibilityStatus() == null) {
            boardPostDto.setVisibilityStatus("PUBLIC");
        }

        boardPostDto.setPinnedYn("N");

        inquirySqlMapper.insertInquiryPost(boardPostDto);

        InquiryDetailDto inquiryDetailDto = new InquiryDetailDto();

        inquiryDetailDto.setPostId(boardPostDto.getPostId());
        inquiryDetailDto.setInquiryCategoryCode(requestDto.getInquiryCategoryCode());
        inquiryDetailDto.setInquiryStatusCode("RECEIVED");

        inquirySqlMapper.insertInquiryDetail(inquiryDetailDto);
    }

    @Override
    public InquiryDetailDto getInquiryDetail(Long postId) {
        inquirySqlMapper.increaseInquiryViewCount(postId);

        return inquirySqlMapper.findInquiryDetail(postId);
    }
}
