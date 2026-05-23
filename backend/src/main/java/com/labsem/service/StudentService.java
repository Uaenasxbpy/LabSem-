package com.labsem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.entity.Student;
import com.labsem.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentMapper studentMapper;

    /**
     * List all students ordered by name ASC.
     */
    public List<Student> listAll() {
        return studentMapper.selectList(
                new LambdaQueryWrapper<Student>().orderByAsc(Student::getName)
        );
    }

    /**
     * Find a student by exact name, create if not found.
     */
    public Student findOrCreate(String name) {
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFKC);
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getName, normalized)
        );
        if (student == null) {
            student = new Student();
            student.setName(normalized);
            studentMapper.insert(student);
        }
        return student;
    }
}
