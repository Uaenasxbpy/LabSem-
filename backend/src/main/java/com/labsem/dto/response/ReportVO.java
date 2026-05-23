package com.labsem.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ReportVO {
    private Long id;
    private String studentName;
    private LocalDate reportDate;
    private String folderName;
    private List<PaperVO> papers;
    private List<FileVO> files;
}
