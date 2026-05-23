package com.labsem.controller;

import com.labsem.entity.PaperPool;
import com.labsem.service.PaperPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PaperPoolController {

    private final PaperPoolService paperPoolService;

    /**
     * GET /api/paper-pool
     * Returns {"papers": [...]}
     * Optional query params: status, keyword
     */
    @GetMapping("/api/paper-pool")
    public Map<String, Object> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        List<PaperPool> papers = paperPoolService.list(status);
        return Map.of("papers", papers);
    }

    /**
     * POST /api/paper-pool
     * Create a new paper pool entry. Form params.
     */
    @PostMapping("/api/paper-pool")
    public PaperPool create(
            @RequestParam("title") String title,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "recommended_by", required = false) String recommendedBy,
            @RequestParam(value = "notes", required = false) String notes
    ) {
        return paperPoolService.create(title, url, recommendedBy, notes);
    }

    /**
     * PUT /api/paper-pool/{id}
     * Update an existing paper pool entry.
     */
    @PutMapping("/api/paper-pool/{id}")
    public PaperPool update(
            @PathVariable Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "recommended_by", required = false) String recommendedBy,
            @RequestParam(value = "notes", required = false) String notes
    ) {
        return paperPoolService.update(id, title, url, recommendedBy, notes);
    }

    /**
     * DELETE /api/paper-pool/{id}
     * Returns {"ok": true, "message": "已删除论文"}
     */
    @DeleteMapping("/api/paper-pool/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        paperPoolService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("message", "已删除论文");
        return result;
    }

    /**
     * PUT /api/paper-pool/{id}/claim
     * Claim a paper for a student.
     * Returns {"ok": true, "claimed_by": "student_name"}
     */
    @PutMapping("/api/paper-pool/{id}/claim")
    public Map<String, Object> claim(
            @PathVariable Long id,
            @RequestParam("claimed_by") String claimedBy
    ) {
        paperPoolService.claim(id, claimedBy);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("claimed_by", claimedBy);
        return result;
    }

    /**
     * PUT /api/paper-pool/{id}/unclaim
     * Unclaim a paper.
     * Returns {"ok": true, "message": "已取消认领"}
     */
    @PutMapping("/api/paper-pool/{id}/unclaim")
    public Map<String, Object> unclaim(@PathVariable Long id) {
        paperPoolService.unclaim(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("message", "已取消认领");
        return result;
    }
}
