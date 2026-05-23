package com.labsem.controller;

import com.labsem.entity.StoredFile;
import com.labsem.mapper.StoredFileMapper;
import com.labsem.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class AdminController {

    private final StoredFileMapper storedFileMapper;
    private final FileStorageService fileStorageService;

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    /**
     * POST /api/admin/cleanup-stale-files
     * Remove file records from the database that no longer exist on disk.
     * Returns {"ok": true, "cleaned_count": N}
     */
    @PostMapping("/api/admin/cleanup-stale-files")
    public Map<String, Object> cleanup() {
        List<StoredFile> allFiles = storedFileMapper.selectList(null);
        int cleanedCount = 0;
        for (StoredFile file : allFiles) {
            if (file.getStoragePath() != null && !Files.exists(Paths.get(file.getStoragePath()))) {
                storedFileMapper.deleteById(file.getId());
                cleanedCount++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("cleaned_count", cleanedCount);
        return result;
    }

    /**
     * POST /api/admin/sync-uploads
     * Count files on disk that are not yet tracked in the database.
     * Returns {"ok": true, "synced_count": N}
     */
    @PostMapping("/api/admin/sync-uploads")
    public Map<String, Object> syncUploads() {
        // Collect all tracked storage paths from DB
        Set<String> trackedPaths = storedFileMapper.selectList(null).stream()
                .map(StoredFile::getStoragePath)
                .filter(p -> p != null)
                .collect(Collectors.toSet());

        // Scan uploads directory for files not in DB
        int untrackedCount = 0;
        Path root = Paths.get(uploadDir);
        if (Files.exists(root) && Files.isDirectory(root)) {
            try (var walk = Files.walk(root)) {
                untrackedCount = (int) walk
                        .filter(Files::isRegularFile)
                        .filter(p -> !trackedPaths.contains(p.toAbsolutePath().toString()))
                        .count();
            } catch (IOException ignored) {
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("synced_count", untrackedCount);
        return result;
    }
}
