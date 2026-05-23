package com.labsem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.common.util.TitleNormalizer;
import com.labsem.constant.AppConstants;
import com.labsem.entity.Paper;
import com.labsem.entity.Report;
import com.labsem.entity.StoredFile;
import com.labsem.mapper.PaperMapper;
import com.labsem.mapper.ReportMapper;
import com.labsem.mapper.StoredFileMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DuplicateDetectionService {

    private final PaperMapper paperMapper;
    private final StoredFileMapper storedFileMapper;
    private final ReportMapper reportMapper;

    private final JaroWinklerDistance jaroWinkler = new JaroWinklerDistance();

    /**
     * Find title duplicates using exact match and fuzzy JaroWinkler matching.
     */
    public List<Map<String, Object>> findTitleDuplicates(String title) {
        String normalized = TitleNormalizer.normalize(title);
        List<Map<String, Object>> duplicates = new ArrayList<>();

        // Query all papers with report info
        List<Paper> allPapers = paperMapper.selectList(
                new LambdaQueryWrapper<Paper>().isNotNull(Paper::getTitleNormalized)
        );

        // Build a map of report_id -> Report for batch loading
        List<Long> reportIds = allPapers.stream()
                .map(Paper::getReportId)
                .distinct()
                .toList();
        Map<Long, Report> reportMap = new HashMap<>();
        if (!reportIds.isEmpty()) {
            List<Report> reports = reportMapper.selectBatchIds(reportIds);
            for (Report r : reports) {
                reportMap.put(r.getId(), r);
            }
        }

        for (Paper paper : allPapers) {
            if (paper.getTitleNormalized() == null || paper.getTitleNormalized().isEmpty()) {
                continue;
            }
            Report report = reportMap.get(paper.getReportId());
            if (report == null) continue;

            if (paper.getTitleNormalized().equals(normalized)) {
                Map<String, Object> dup = new HashMap<>();
                dup.put("paper_title", paper.getTitleRaw());
                dup.put("student_name", report.getStudentName());
                dup.put("report_date", report.getReportDate() != null ? report.getReportDate().toString() : null);
                dup.put("match_type", "title_exact");
                dup.put("score", 1.0);
                duplicates.add(dup);
                continue;
            }

            double score = jaroWinkler.apply(normalized, paper.getTitleNormalized());
            if (score >= AppConstants.TITLE_SIMILARITY_THRESHOLD) {
                Map<String, Object> dup = new HashMap<>();
                dup.put("paper_title", paper.getTitleRaw());
                dup.put("student_name", report.getStudentName());
                dup.put("report_date", report.getReportDate() != null ? report.getReportDate().toString() : null);
                dup.put("match_type", "title_similar");
                dup.put("score", Math.round(score * 10000.0) / 10000.0);
                duplicates.add(dup);
            }
        }

        duplicates.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        return duplicates;
    }

    /**
     * Find PDF hash duplicates by querying StoredFile where file_type="pdf" and hash matches.
     */
    public List<Map<String, Object>> findPdfHashDuplicates(String fileHash) {
        List<Map<String, Object>> duplicates = new ArrayList<>();

        List<StoredFile> matchingFiles = storedFileMapper.selectList(
                new LambdaQueryWrapper<StoredFile>()
                        .eq(StoredFile::getFileType, "pdf")
                        .eq(StoredFile::getFileHash, fileHash)
        );

        if (matchingFiles.isEmpty()) {
            return duplicates;
        }

        // Batch load reports and papers
        List<Long> reportIds = matchingFiles.stream()
                .map(StoredFile::getReportId)
                .distinct()
                .toList();
        Map<Long, Report> reportMap = new HashMap<>();
        if (!reportIds.isEmpty()) {
            List<Report> reports = reportMapper.selectBatchIds(reportIds);
            for (Report r : reports) {
                reportMap.put(r.getId(), r);
            }
        }

        List<Long> paperIds = matchingFiles.stream()
                .map(StoredFile::getPaperId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, Paper> paperMap = new HashMap<>();
        if (!paperIds.isEmpty()) {
            List<Paper> papers = paperMapper.selectBatchIds(paperIds);
            for (Paper p : papers) {
                paperMap.put(p.getId(), p);
            }
        }

        for (StoredFile file : matchingFiles) {
            Report report = reportMap.get(file.getReportId());
            Paper paper = file.getPaperId() != null ? paperMap.get(file.getPaperId()) : null;

            Map<String, Object> dup = new HashMap<>();
            dup.put("paper_title", paper != null ? paper.getTitleRaw() : file.getOriginalName());
            dup.put("student_name", report != null ? report.getStudentName() : null);
            dup.put("report_date", report != null && report.getReportDate() != null ? report.getReportDate().toString() : null);
            dup.put("match_type", "file_hash");
            dup.put("score", 1.0);
            duplicates.add(dup);
        }

        return duplicates;
    }
}
