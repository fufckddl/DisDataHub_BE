package com.hub.gisdatahub.board.service;

import com.hub.gisdatahub.board.dto.GeoCodeResponse;
import com.hub.gisdatahub.board.dto.LocationSearchResponse;

import java.util.List;

public interface LocationService {

    public GeoCodeResponse geocode(String address);

    public List<LocationSearchResponse> search(String keyword);
}