package com.hub.gisdatahub.board.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hub.gisdatahub.board.dto.AdminInquiryAnswerRequestDto;
import com.hub.gisdatahub.board.dto.BoardPostDto;
import com.hub.gisdatahub.board.dto.BoardReplyDto;
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

    @Override
    public List<InquiryDetailDto> getAdminInquiryList() {
        return inquirySqlMapper.findAdminInquiryList();
    }

    @Override
    public InquiryDetailDto getAdminInquiryDetail(Long postId) {
        return inquirySqlMapper.findAdminInquiryDetail(postId);
    }

    @Transactional
    @Override
    public void saveAdminInquiryAnswer(Long postId, AdminInquiryAnswerRequestDto requestDto) {
        String statusCode = requestDto.getInquiryStatusCode();

        if (statusCode == null) {
            statusCode = "ANSWERED";
        }

        if (
            !"RECEIVED".equals(statusCode) &&
            !"CHECKING".equals(statusCode) &&
            !"ANSWERED".equals(statusCode)
        ) {
            throw new RuntimeException("올바르지 않은 문의 상태값입니다.");
        }

        String answerContent = requestDto.getAnswerContent();

        if ("ANSWERED".equals(statusCode) && (answerContent == null || answerContent.trim().isEmpty())) {
            throw new RuntimeException("답변 완료 상태에서는 답변 내용을 입력해야 합니다.");
        }

        if (answerContent != null && !answerContent.trim().isEmpty()) {
            BoardReplyDto boardReplyDto = new BoardReplyDto();

            boardReplyDto.setPostId(postId);
            boardReplyDto.setUserId(requestDto.getAdminUserId());
            boardReplyDto.setReplyWriterName(
                requestDto.getReplyWriterName() == null ? "관리자" : requestDto.getReplyWriterName()
            );
            boardReplyDto.setContent(answerContent);

            inquirySqlMapper.insertInquiryAnswer(boardReplyDto);
        }

        int updateCount = inquirySqlMapper.updateInquiryStatus(postId, statusCode);

        if (updateCount == 0) {
            throw new RuntimeException("상태를 변경할 문의 게시글을 찾을 수 없습니다.");
        }
    }
}
