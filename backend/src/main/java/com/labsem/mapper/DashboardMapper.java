package com.labsem.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    List<Map<String, Object>> selectMonthlyReportCount();

    List<Map<String, Object>> selectMonthlyPaperCount();

    List<Map<String, Object>> selectStudentReportCount();

    List<Map<String, Object>> selectStudentPaperCount();
}
