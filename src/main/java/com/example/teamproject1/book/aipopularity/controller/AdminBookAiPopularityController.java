package com.example.teamproject1.book.aipopularity.controller;

import com.example.teamproject1.book.aipopularity.dto.AdminBookAiPopularityDtos;
import com.example.teamproject1.book.aipopularity.service.AdminBookAiPopularityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/books")
public class AdminBookAiPopularityController {

    private final AdminBookAiPopularityService
            adminBookAiPopularityService;

    @PostMapping("/{isbn13}/ai-popularity")
    public ResponseEntity<AdminBookAiPopularityDtos.Response>
    analyzePopularity(
            @PathVariable(name = "isbn13")
            String isbn13,

            @RequestBody
            AdminBookAiPopularityDtos.Request request
    ) {
        return ResponseEntity.ok(
                adminBookAiPopularityService
                        .analyze(
                                isbn13,
                                request
                        )
        );
    }
}
