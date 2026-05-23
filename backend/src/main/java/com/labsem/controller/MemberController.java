package com.labsem.controller;

import com.labsem.entity.Member;
import com.labsem.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /**
     * GET /api/members
     * Returns {"members": [...]}
     */
    @GetMapping("/api/members")
    public Map<String, Object> list() {
        List<Member> members = memberService.list();
        return Map.of("members", members);
    }

    /**
     * POST /api/members
     * Create a new member.
     */
    @PostMapping("/api/members")
    public Member create(
            @RequestParam("name") String name,
            @RequestParam(value = "email", required = false) String email
    ) {
        return memberService.create(name, email);
    }

    /**
     * PUT /api/members/{id}
     * Update an existing member.
     */
    @PutMapping("/api/members/{id}")
    public Member update(
            @PathVariable Long id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "email", required = false) String email
    ) {
        return memberService.update(id, name, email);
    }

    /**
     * DELETE /api/members/{id}
     * Returns {"ok": true, "message": "已删除成员"}
     */
    @DeleteMapping("/api/members/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        memberService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("message", "已删除成员");
        return result;
    }
}
