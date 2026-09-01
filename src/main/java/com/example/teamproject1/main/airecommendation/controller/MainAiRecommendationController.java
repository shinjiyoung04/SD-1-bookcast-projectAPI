package com.example.teamproject1.main.airecommendation.controller;

import com.example.teamproject1.main.airecommendation.dto.MainAiRecommendationDtos;
import com.example.teamproject1.main.airecommendation.service.MainAiRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/main")
public class MainAiRecommendationController {

    private final MainAiRecommendationService
            mainAiRecommendationService;

    @PostMapping("/ai-recommendations")
    public ResponseEntity<
            List<MainAiRecommendationDtos.RecommendedBook>
            >
    recommend(
            @RequestBody
            MainAiRecommendationDtos.Request request
    ) {
        return ResponseEntity.ok(
                mainAiRecommendationService
                        .recommend(request)
        );
    }
}
