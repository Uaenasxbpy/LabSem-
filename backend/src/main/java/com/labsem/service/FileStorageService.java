package com.labsem.service;

import com.labsem.common.util.FileUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Map;

@Service
public class FileStorageService {

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    /**
     * Ensure the upload root directory exists and return its Path.
     */
    public Path ensureStorageRoot() {
        Path root = Paths.get(uploadDir);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload directory: " + root, e);
        }
        return root;
    }

    /**
     * Create a unique report folder.
     * Folder name: {date}_{slugified_name}, appended with _1, _2 if exists.
     * Returns a pair of (folderName, folderPath).
     */
    public Map.Entry<String, Path> createUniqueReportFolder(java.time.LocalDate reportDate, String studentName) {
        Path root = ensureStorageRoot();
        String base = reportDate.toString() + "_" + FileUtil.slugify(studentName);
        String folderName = base;
        int index = 1;
        while (Files.exists(root.resolve(folderName))) {
            folderName = base + "_" + index;
            index++;
        }
        Path folderPath = root.resolve(folderName);
        try {
            Files.createDirectories(folderPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create report folder: " + folderPath, e);
        }
        return new AbstractMap.SimpleEntry<>(folderName, folderPath);
    }

    /**
     * Store a file in the given folder with the given storage name.
     * Returns the absolute path of the stored file.
     */
    public String storeFile(Path folderPath, String storageName, byte[] content) {
        Path target = folderPath.resolve(storageName);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + target, e);
        }
        return target.toAbsolutePath().toString();
    }

    /**
     * Delete an entire report folder by folder name.
     */
    public void deleteReportFolder(String folderName) {
        Path folder = ensureStorageRoot().resolve(folderName);
        if (Files.exists(folder) && Files.isDirectory(folder)) {
            deleteDirectoryRecursively(folder);
        }
    }

    /**
     * Get the lab files directory, creating it if needed.
     */
    public Path getLabFilesDir() {
        Path dir = ensureStorageRoot().resolve("lab_files");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create lab files directory: " + dir, e);
        }
        return dir;
    }

    /**
     * Store a lab file. Storage name = {hash12}_{fileName}.
     * Returns the absolute path.
     */
    public String storeLabFile(String storageName, byte[] content) {
        Path target = getLabFilesDir().resolve(storageName);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store lab file: " + target, e);
        }
        return target.toAbsolutePath().toString();
    }

    /**
     * Delete a file by its absolute storage path.
     */
    public void deleteFile(String storagePath) {
        if (storagePath == null) return;
        Path path = Paths.get(storagePath);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Log but don't throw - file may already be gone
        }
    }

    private void deleteDirectoryRecursively(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    for (Path child : stream.toList()) {
                        deleteDirectoryRecursively(child);
                    }
                }
            }
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            // Log but don't throw
        }
    }
}
