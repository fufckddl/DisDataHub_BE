package com.hub.gisdatahub.board.controller;

import com.hub.gisdatahub.board.dto.GeoCodeResponse;
import com.hub.gisdatahub.board.dto.LocationSearchResponse;
import com.hub.gisdatahub.board.service.LocationService;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/api/location/geocode")
    public GeoCodeResponse geocode(@RequestParam String address) {
        return locationService.geocode(address);
    }

    @GetMapping("/api/location/search")
    public List<LocationSearchResponse> search(@RequestParam String keyword) {
        return locationService.search(keyword);
    }
}