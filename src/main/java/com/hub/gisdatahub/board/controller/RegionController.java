package com.hub.gisdatahub.board.controller;

import com.hub.gisdatahub.board.dto.RegionResponse;
import com.hub.gisdatahub.board.service.RegionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @GetMapping("/api/regions/sido")
    public List<RegionResponse> getSidoList() {
        return regionService.getSidoList();
    }

    @GetMapping("/api/regions/sigungu")
    public List<RegionResponse> getSigunguList(@RequestParam String sidoCode) {
        return regionService.getSigunguList(sidoCode);
    }

    @GetMapping("/api/regions/eupmyeondong")
    public List<RegionResponse> getEupmyeondongList(@RequestParam String sigunguCode) {
        return regionService.getEupmyeondongList(sigunguCode);
    }
}