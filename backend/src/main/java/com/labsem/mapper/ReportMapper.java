package com.labsem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labsem.entity.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    List<Report> searchByKeyword(@Param("keyword") String keyword,
                                 @Param("normalizedKeyword") String normalizedKeyword,
                                 @Param("limit") int limit);
}
