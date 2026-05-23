package com.labsem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.labsem.mapper")
public class LabSemApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabSemApplication.class, args);
    }
}
