package com.hub.gisdatahub.download.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasetDownloadMapper {

    public Integer findDownloadCountById(Integer id);
}
