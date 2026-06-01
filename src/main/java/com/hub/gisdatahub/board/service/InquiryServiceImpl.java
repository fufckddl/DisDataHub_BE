package com.hub.gisdatahub.board.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

}
