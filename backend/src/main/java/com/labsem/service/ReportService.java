package com.labsem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.common.exception.BusinessException;
import com.labsem.common.util.FileUtil;
import com.labsem.common.util.TitleNormalizer;
import com.labsem.entity.Paper;
import com.labsem.entity.Report;
import com.labsem.entity.StoredFile;
import com.labsem.entity.Student;
import com.labsem.mapper.PaperMapper;
import com.labsem.mapper.ReportMapper;
import com.labsem.mapper.StoredFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final PaperMapper paperMapper;
    private final StoredFileMapper storedFileMapper;
    private final StudentService studentService;
    private final FileStorageService fileStorageService;
    private final DuplicateDetectionService duplicateDetectionService;

    /**
     * Search reports with optional filters. Loads papers and files for each report.
     */
    public List<Map<String, Object>> search(String keyword, String studentName,
                                             LocalDate startDate, LocalDate endDate, int limit) {
        List<Report> reports;

        if (keyword != null && !keyword.trim().isEmpty()) {
            // Use custom XML query for keyword search
            String normalizedKeyword = TitleNormalizer.normalize(keyword);
            reports = reportMapper.searchByKeyword(keyword.trim(), normalizedKeyword, limit);
        } else {
            LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
            if (studentName != null && !studentName.trim().isEmpty()) {
                wrapper.like(Report::getStudentName, studentName.trim());
            }
            if (startDate != null) {
                wrapper.ge(Report::getReportDate, startDate);
            }
            if (endDate != null) {
                wrapper.le(Report::getReportDate, endDate);
            }
            wrapper.orderByDesc(Report::getReportDate).orderByDesc(Report::getId);
            wrapper.last("LIMIT " + limit);
            reports = reportMapper.selectList(wrapper);
        }

        if (reports.isEmpty()) {
            return List.of();
        }

        // Batch load papers and files for all reports
        List<Long> reportIds = reports.stream().map(Report::getId).toList();

        Map<Long, List<Paper>> papersMap = new HashMap<>();
        List<Paper> allPapers = paperMapper.selectList(
                new LambdaQueryWrapper<Paper>().in(Paper::getReportId, reportIds)
        );
        for (Paper p : allPapers) {
            papersMap.computeIfAbsent(p.getReportId(), k -> new ArrayList<>()).add(p);
        }

        Map<Long, List<StoredFile>> filesMap = new HashMap<>();
        List<StoredFile> allFiles = storedFileMapper.selectList(
                new LambdaQueryWrapper<StoredFile>().in(StoredFile::getReportId, reportIds)
        );
        for (StoredFile f : allFiles) {
            filesMap.computeIfAbsent(f.getReportId(), k -> new ArrayList<>()).add(f);
        }

        // Build result
        List<Map<String, Object>> result = new ArrayList<>();
        for (Report report : reports) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", report.getId());
            item.put("student_id", report.getStudentId());
            item.put("student_name", report.getStudentName());
            item.put("report_date", report.getReportDate());
            item.put("folder_name", report.getFolderName());
            item.put("created_at", report.getCreatedAt());
            item.put("papers", papersMap.getOrDefault(report.getId(), List.of()));
            item.put("files", filesMap.getOrDefault(report.getId(), List.of()));
            result.add(item);
        }

        return result;
    }

    /**
     * Create a report with papers and files. Handles duplicate detection.
     *
     * @return Map with ok, need_confirm, warning, duplicates, report_id, folder_name
     */
    @Transactional
    public Map<String, Object> createReport(String studentName, LocalDate reportDate,
                                             List<String> paperTitles, List<byte[]> paperPdfBytes,
                                             List<String> paperPdfNames, byte[] pptBytes, String pptName,
                                             boolean confirmDuplicate) {
        // 1. Clean and validate titles
        List<String> cleanTitles = paperTitles.stream()
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .toList();
        if (cleanTitles.isEmpty()) {
            throw new BusinessException(400, "至少需要填写一篇论文标题");
        }
        if (paperPdfBytes.size() != cleanTitles.size()) {
            throw new BusinessException(400, "每篇论文需要对应一个PDF文件");
        }

        // 2. Check for duplicates
        List<Map<String, Object>> duplicateHints = new ArrayList<>();
        List<String> pdfHashes = new ArrayList<>();

        for (int idx = 0; idx < cleanTitles.size(); idx++) {
            String title = cleanTitles.get(idx);
            byte[] content = paperPdfBytes.get(idx);
            if (content == null || content.length == 0) {
                throw new BusinessException(400, "第 " + (idx + 1) + " 篇论文PDF为空");
            }

            String fileHash = FileUtil.sha256(content);
            pdfHashes.add(fileHash);

            // Check title duplicates
            List<Map<String, Object>> titleDups = duplicateDetectionService.findTitleDuplicates(title);
            for (Map<String, Object> dup : titleDups) {
                Map<String, Object> hint = new HashMap<>(dup);
                hint.put("input_title", title);
                duplicateHints.add(hint);
            }

            // Check PDF hash duplicates
            List<Map<String, Object>> hashDups = duplicateDetectionService.findPdfHashDuplicates(fileHash);
            for (Map<String, Object> dup : hashDups) {
                Map<String, Object> hint = new HashMap<>(dup);
                hint.put("input_title", title);
                duplicateHints.add(hint);
            }
        }

        // 3. If duplicates found and not confirmed, return confirmation request
        if (!duplicateHints.isEmpty() && !confirmDuplicate) {
            Map<String, Object> first = duplicateHints.get(0);
            String warning = "警告！该论文已于 " + first.get("report_date") + " 由 "
                    + first.get("student_name") + " 同学汇报过，是否确认重复上传？";
            Map<String, Object> result = new HashMap<>();
            result.put("ok", false);
            result.put("need_confirm", true);
            result.put("warning", warning);
            result.put("duplicates", duplicateHints);
            return result;
        }

        // 4. Create unique folder
        Map.Entry<String, Path> folderEntry = fileStorageService.createUniqueReportFolder(reportDate, studentName);
        String folderName = folderEntry.getKey();
        Path folderPath = folderEntry.getValue();

        // 5. Find or create student
        Student student = studentService.findOrCreate(studentName);

        // 6. Create Report entity
        Report report = new Report();
        report.setStudentId(student.getId());
        report.setStudentName(student.getName());
        report.setReportDate(reportDate);
        report.setFolderName(folderName);
        reportMapper.insert(report);

        // 7. For each paper: create Paper, store PDF, create StoredFile
        for (int idx = 0; idx < cleanTitles.size(); idx++) {
            String title = cleanTitles.get(idx);
            byte[] content = paperPdfBytes.get(idx);
            String fileHash = pdfHashes.get(idx);

            boolean hasDup = duplicateHints.stream()
                    .anyMatch(h -> title.equals(h.get("input_title")));

            Paper paper = new Paper();
            paper.setReportId(report.getId());
            paper.setTitleRaw(title);
            paper.setTitleNormalized(TitleNormalizer.normalize(title));
            paper.setDuplicateStatus(hasDup ? "duplicate" : "unique");
            paperMapper.insert(paper);

            String storageName = FileUtil.renameForStorage("PDF", idx, paperPdfNames.get(idx));
            String storagePath = fileStorageService.storeFile(folderPath, storageName, content);

            StoredFile storedFile = new StoredFile();
            storedFile.setReportId(report.getId());
            storedFile.setPaperId(paper.getId());
            storedFile.setFileType("pdf");
            storedFile.setOriginalName(paperPdfNames.get(idx));
            storedFile.setStorageName(storageName);
            storedFile.setStoragePath(storagePath);
            storedFile.setFileHash(fileHash);
            storedFileMapper.insert(storedFile);
        }

        // 8. If PPT provided: store PPT, create StoredFile
        if (pptBytes != null && pptBytes.length > 0 && pptName != null && !pptName.isEmpty()) {
            String pptHash = FileUtil.sha256(pptBytes);
            String pptStorageName = FileUtil.renameForStorage("PPT", 0, pptName);
            String pptStoragePath = fileStorageService.storeFile(folderPath, pptStorageName, pptBytes);

            StoredFile pptFile = new StoredFile();
            pptFile.setReportId(report.getId());
            pptFile.setPaperId(null);
            pptFile.setFileType("ppt");
            pptFile.setOriginalName(pptName);
            pptFile.setStorageName(pptStorageName);
            pptFile.setStoragePath(pptStoragePath);
            pptFile.setFileHash(pptHash);
            storedFileMapper.insert(pptFile);
        }

        // 9. Return success
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("need_confirm", false);
        result.put("report_id", report.getId());
        result.put("folder_name", folderName);
        result.put("duplicates", duplicateHints);
        return result;
    }

    /**
     * Delete a report with all its files and folder.
     */
    @Transactional
    public void deleteReport(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw BusinessException.notFound("汇报记录不存在");
        }

        // Delete physical files
        List<StoredFile> files = storedFileMapper.selectList(
                new LambdaQueryWrapper<StoredFile>().eq(StoredFile::getReportId, reportId)
        );
        for (StoredFile file : files) {
            fileStorageService.deleteFile(file.getStoragePath());
        }

        // Delete folder
        if (report.getFolderName() != null) {
            fileStorageService.deleteReportFolder(report.getFolderName());
        }

        // Delete from DB (cascade handles papers/files)
        reportMapper.deleteById(reportId);
    }
}
