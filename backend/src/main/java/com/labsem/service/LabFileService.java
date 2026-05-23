package com.labsem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.common.exception.BusinessException;
import com.labsem.common.util.FileUtil;
import com.labsem.entity.LabFile;
import com.labsem.mapper.LabFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LabFileService {

    private final LabFileMapper labFileMapper;
    private final FileStorageService fileStorageService;

    /**
     * List all unique tags from lab files, sorted.
     */
    public List<String> listTags() {
        List<LabFile> allFiles = labFileMapper.selectList(
                new LambdaQueryWrapper<LabFile>()
                        .isNotNull(LabFile::getTags)
                        .ne(LabFile::getTags, "")
        );
        Set<String> tagSet = new TreeSet<>();
        for (LabFile file : allFiles) {
            if (file.getTags() != null && !file.getTags().isEmpty()) {
                Arrays.stream(file.getTags().split(","))
                        .map(String::trim)
                        .filter(t -> !t.isEmpty())
                        .forEach(tagSet::add);
            }
        }
        return List.copyOf(tagSet);
    }

    /**
     * List lab files with optional keyword and tag filters.
     */
    public List<LabFile> list(String keyword, String tag) {
        LambdaQueryWrapper<LabFile> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w
                    .like(LabFile::getTitle, kw)
                    .or().like(LabFile::getDescription, kw)
                    .or().like(LabFile::getTags, kw)
            );
        }
        if (tag != null && !tag.trim().isEmpty()) {
            wrapper.like(LabFile::getTags, tag.trim());
        }
        wrapper.orderByDesc(LabFile::getCreatedAt);
        return labFileMapper.selectList(wrapper);
    }

    /**
     * Upload a lab file.
     * SHA-256 hash, storage name = {hash12}_{filename}, store in uploads/lab_files/.
     */
    public LabFile upload(String title, String description, String tags, String uploadedBy,
                          byte[] fileBytes, String fileName) {
        String fileHash = FileUtil.sha256(fileBytes);
        String storageName = fileHash.substring(0, 12) + "_" + fileName;
        String storagePath = fileStorageService.storeLabFile(storageName, fileBytes);

        LabFile labFile = new LabFile();
        labFile.setTitle(title.trim());
        labFile.setDescription(description != null && !description.trim().isEmpty() ? description.trim() : null);
        labFile.setTags(tags != null ? tags.trim() : "");
        labFile.setOriginalName(fileName);
        labFile.setStorageName(storageName);
        labFile.setStoragePath(storagePath);
        labFile.setFileSize((long) fileBytes.length);
        labFile.setFileHash(fileHash);
        labFile.setUploadedBy(uploadedBy != null && !uploadedBy.trim().isEmpty() ? uploadedBy.trim() : null);
        labFileMapper.insert(labFile);
        return labFile;
    }

    /**
     * Update lab file metadata. Throws 404 if not found.
     */
    public LabFile update(Long fileId, String title, String description, String tags, String uploadedBy) {
        LabFile labFile = labFileMapper.selectById(fileId);
        if (labFile == null) {
            throw new BusinessException.notFound("文件不存在");
        }
        labFile.setTitle(title.trim());
        labFile.setDescription(description != null && !description.trim().isEmpty() ? description.trim() : null);
        labFile.setTags(tags != null ? tags.trim() : "");
        labFile.setUploadedBy(uploadedBy != null && !uploadedBy.trim().isEmpty() ? uploadedBy.trim() : null);
        labFileMapper.updateById(labFile);
        return labFile;
    }

    /**
     * Delete a lab file (physical file + DB row). Throws 404 if not found.
     */
    public void delete(Long fileId) {
        LabFile labFile = labFileMapper.selectById(fileId);
        if (labFile == null) {
            throw new BusinessException.notFound("文件不存在");
        }
        fileStorageService.deleteFile(labFile.getStoragePath());
        labFileMapper.deleteById(fileId);
    }

    /**
     * Get lab file for download. Checks file exists on disk, auto-deletes DB if missing.
     */
    public LabFile getForDownload(Long fileId) {
        LabFile labFile = labFileMapper.selectById(fileId);
        if (labFile == null) {
            throw new BusinessException.notFound("文件不存在");
        }
        if (!Files.exists(Paths.get(labFile.getStoragePath()))) {
            labFileMapper.deleteById(fileId);
            throw new BusinessException.notFound("文件已不存在，记录已自动清理");
        }
        return labFile;
    }

    /**
     * Get lab file for preview. Same validation as download.
     */
    public LabFile getForPreview(Long fileId) {
        return getForDownload(fileId);
    }
}
