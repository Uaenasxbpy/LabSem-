package com.labsem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.entity.Paper;
import com.labsem.entity.Report;
import com.labsem.entity.StoredFile;
import com.labsem.mapper.PaperMapper;
import com.labsem.mapper.ReportMapper;
import com.labsem.mapper.StoredFileMapper;
import com.labsem.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final StoredFileMapper storedFileMapper;
    private final ReportMapper reportMapper;
    private final PaperMapper paperMapper;
    private final AccessLogService accessLogService;

    /**
     * GET /api/files
     * Returns a raw array of all stored files with report context.
     * Each item: {id, original_name, file_type, report_id, report_student_name, report_date, paper_title}
     */
    @GetMapping("/api/files")
    public List<Map<String, Object>> listFiles() {
        List<StoredFile> allFiles = storedFileMapper.selectList(
                new LambdaQueryWrapper<StoredFile>().orderByDesc(StoredFile::getId));
        if (allFiles.isEmpty()) {
            return List.of();
        }

        // Batch load reports
        List<Long> reportIds = allFiles.stream().map(StoredFile::getReportId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Report> reportMap = new HashMap<>();
        if (!reportIds.isEmpty()) {
            reportMapper.selectBatchIds(reportIds).forEach(r -> reportMap.put(r.getId(), r));
        }

        // Batch load papers
        List<Long> paperIds = allFiles.stream().map(StoredFile::getPaperId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Paper> paperMap = new HashMap<>();
        if (!paperIds.isEmpty()) {
            paperMapper.selectBatchIds(paperIds).forEach(p -> paperMap.put(p.getId(), p));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (StoredFile f : allFiles) {
            Report report = f.getReportId() != null ? reportMap.get(f.getReportId()) : null;
            Paper paper = f.getPaperId() != null ? paperMap.get(f.getPaperId()) : null;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", f.getId());
            item.put("original_name", f.getOriginalName());
            item.put("file_type", f.getFileType());
            item.put("report_id", f.getReportId());
            item.put("report_student_name", report != null ? report.getStudentName() : null);
            item.put("report_date", report != null ? report.getReportDate() : null);
            item.put("paper_title", paper != null ? paper.getTitleRaw() : null);
            result.add(item);
        }
        return result;
    }

    /**
     * GET /api/files/{id}/preview
     * Preview a file inline (e.g., PDF in browser).
     */
    @GetMapping("/api/files/{id}/preview")
    public ResponseEntity<Resource> preview(
            @PathVariable Long id,
            @RequestParam(value = "ip", required = false) String ip
    ) {
        StoredFile file = storedFileMapper.selectById(id);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        if (!Files.exists(Paths.get(file.getStoragePath()))) {
            return ResponseEntity.notFound().build();
        }

        // Resolve report/paper info for logging
        Report report = file.getReportId() != null ? reportMapper.selectById(file.getReportId()) : null;
        Paper paper = file.getPaperId() != null ? paperMapper.selectById(file.getPaperId()) : null;

        accessLogService.log(ip, "preview", file);

        Resource resource = new FileSystemResource(file.getStoragePath());
        MediaType mediaType = resolveMediaType(file.getOriginalName());
        String encodedName = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    /**
     * GET /api/files/{id}/download
     * Download a file as attachment.
     */
    @GetMapping("/api/files/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @RequestParam(value = "ip", required = false) String ip
    ) {
        StoredFile file = storedFileMapper.selectById(id);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        if (!Files.exists(Paths.get(file.getStoragePath()))) {
            return ResponseEntity.notFound().build();
        }

        accessLogService.log(ip, "download", file);

        Resource resource = new FileSystemResource(file.getStoragePath());
        String encodedName = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    private MediaType resolveMediaType(String filename) {
        if (filename == null) return MediaType.APPLICATION_OCTET_STREAM;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".txt")) return MediaType.TEXT_PLAIN;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return MediaType.TEXT_HTML;
        if (lower.endsWith(".ppt")) return MediaType.parseMediaType("application/vnd.ms-powerpoint");
        if (lower.endsWith(".pptx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
