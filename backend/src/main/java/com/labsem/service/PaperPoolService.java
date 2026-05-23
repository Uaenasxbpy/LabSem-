package com.labsem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.common.exception.BusinessException;
import com.labsem.entity.PaperPool;
import com.labsem.mapper.PaperPoolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaperPoolService {

    private final PaperPoolMapper paperPoolMapper;

    /**
     * List paper pool entries with optional status filter, ordered by created_at DESC.
     */
    public List<PaperPool> list(String status) {
        LambdaQueryWrapper<PaperPool> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PaperPool::getStatus, status);
        }
        wrapper.orderByDesc(PaperPool::getCreatedAt);
        return paperPoolMapper.selectList(wrapper);
    }

    /**
     * Create a paper pool entry.
     */
    public PaperPool create(String title, String url, String recommendedBy, String notes) {
        PaperPool paper = new PaperPool();
        paper.setTitle(title.trim());
        paper.setUrl(url != null && !url.trim().isEmpty() ? url.trim() : null);
        paper.setRecommendedBy(recommendedBy.trim());
        paper.setNotes(notes != null && !notes.trim().isEmpty() ? notes.trim() : null);
        paper.setStatus("available");
        paperPoolMapper.insert(paper);
        return paper;
    }

    /**
     * Update a paper pool entry. Throws 404 if not found.
     */
    public PaperPool update(Long paperId, String title, String url, String recommendedBy, String notes) {
        PaperPool paper = paperPoolMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException.notFound("论文不存在");
        }
        paper.setTitle(title.trim());
        paper.setUrl(url != null && !url.trim().isEmpty() ? url.trim() : null);
        paper.setRecommendedBy(recommendedBy.trim());
        paper.setNotes(notes != null && !notes.trim().isEmpty() ? notes.trim() : null);
        paperPoolMapper.updateById(paper);
        return paper;
    }

    /**
     * Delete a paper pool entry. Throws 404 if not found.
     */
    public void delete(Long paperId) {
        PaperPool paper = paperPoolMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException.notFound("论文不存在");
        }
        paperPoolMapper.deleteById(paperId);
    }

    /**
     * Claim a paper. Checks status=="available", sets claimed_by and status="claimed".
     */
    public void claim(Long paperId, String claimedBy) {
        PaperPool paper = paperPoolMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException.notFound("论文不存在");
        }
        if (!"available".equals(paper.getStatus())) {
            throw new BusinessException(400, "该论文已被认领");
        }
        paper.setClaimedBy(claimedBy.trim());
        paper.setStatus("claimed");
        paperPoolMapper.updateById(paper);
    }

    /**
     * Unclaim a paper. Checks status=="claimed", clears claimed_by, sets status="available".
     */
    public void unclaim(Long paperId) {
        PaperPool paper = paperPoolMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException.notFound("论文不存在");
        }
        if (!"claimed".equals(paper.getStatus())) {
            throw new BusinessException(400, "该论文未被认领");
        }
        paper.setClaimedBy(null);
        paper.setStatus("available");
        paperPoolMapper.updateById(paper);
    }
}
