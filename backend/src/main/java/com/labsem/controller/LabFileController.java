package com.labsem.controller;

import com.labsem.common.exception.BusinessException;
import com.labsem.entity.LabFile;
import com.labsem.service.LabFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LabFileController {

    private final LabFileService labFileService;

    /**
     * GET /api/lab-files/tags
     * Returns a list of all unique tags.
     */
    @GetMapping("/api/lab-files/tags")
    public List<String> listTags() {
        return labFileService.listTags();
    }

    /**
     * GET /api/lab-files
     * Returns {"files": [...]}
     * Optional query params: keyword, tag
     */
    @GetMapping("/api/lab-files")
    public Map<String, Object> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "tag", required = false) String tag
    ) {
        List<LabFile> files = labFileService.list(keyword, tag);
        return Map.of("files", files);
    }

    /**
     * POST /api/lab-files
     * Upload a new lab file. Form params + file.
     */
    @PostMapping("/api/lab-files")
    public LabFile upload(
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "uploaded_by", required = false) String uploadedBy,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            return labFileService.upload(title, description, tags, uploadedBy,
                    file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new BusinessException(400, "文件读取失败: " + e.getMessage());
        }
    }

    /**
     * PUT /api/lab-files/{id}
     * Update lab file metadata (not the file itself).
     */
    @PutMapping("/api/lab-files/{id}")
    public LabFile update(
            @PathVariable Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "uploaded_by", required = false) String uploadedBy
    ) {
        return labFileService.update(id, title, description, tags, uploadedBy);
    }

    /**
     * DELETE /api/lab-files/{id}
     * Returns {"ok": true, "message": "已删除文件"}
     */
    @DeleteMapping("/api/lab-files/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        labFileService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("message", "已删除文件");
        return result;
    }

    /**
     * GET /api/lab-files/{id}/download
     * Download a lab file.
     */
    @GetMapping("/api/lab-files/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        LabFile labFile = labFileService.getForDownload(id);
        Resource resource = new FileSystemResource(labFile.getStoragePath());
        String encodedName = URLEncoder.encode(labFile.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    /**
     * GET /api/lab-files/{id}/preview
     * Preview a lab file inline (e.g., PDF in browser).
     */
    @GetMapping("/api/lab-files/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        LabFile labFile = labFileService.getForPreview(id);
        Resource resource = new FileSystemResource(labFile.getStoragePath());
        MediaType mediaType = resolveMediaType(labFile.getOriginalName());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''"
                        + URLEncoder.encode(labFile.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20"))
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
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
