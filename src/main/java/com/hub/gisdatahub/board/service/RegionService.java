package com.hub.gisdatahub.board.service;

import com.hub.gisdatahub.board.dto.RegionResponse;

import java.util.List;

public interface RegionService {

    public List<RegionResponse> getSidoList();

    public List<RegionResponse> getSigunguList(String sidoCode);

    public List<RegionResponse> getEupmyeondongList(String sigunguCode);
}