package com.labsem.controller;

import com.labsem.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * GET /api/students
     * Returns {"students": [{"id":1,"name":"张三"}, ...]}
     */
    @GetMapping("/api/students")
    public Map<String, Object> listStudents() {
        List<Map<String, Object>> students = studentService.listAll().stream()
                .map(s -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    return m;
                })
                .toList();
        return Map.of("students", students);
    }
}
