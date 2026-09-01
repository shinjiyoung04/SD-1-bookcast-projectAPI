package com.example.teamproject1.book.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DbTestController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/api/db-test")
    public Map<String, Object> dbTest() {
        String databaseName = jdbcTemplate.queryForObject(
                "SELECT DATABASE()",
                String.class
        );

        Integer applicationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hope_applications",
                Integer.class
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "DB connection success");
        result.put("database", databaseName);
        result.put("hopeApplicationCount", applicationCount);

        return result;
    }
}