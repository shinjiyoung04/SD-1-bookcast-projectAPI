package com.example.teamproject1.main.controller;

import com.example.teamproject1.main.dto.MainHotTrendBookResponse;
import com.example.teamproject1.main.dto.MainPopularBookResponse;
import com.example.teamproject1.main.service.MainPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/main")
public class MainPageController {

    private final MainPageService mainPageService;

    /**
     * 최근 30일 고양시 인기 대출도서
     *
     * GET /api/main/popular-books?limit=20
     */
    @GetMapping("/popular-books")
    public ResponseEntity<List<MainPopularBookResponse>> getPopularBooks(
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return ResponseEntity.ok(
                mainPageService.getPopularBooks(limit)
        );
    }

    /**
     * 정보나루 대출 급상승 도서
     *
     * GET /api/main/hot-trend-books?limit=15
     */
    @GetMapping("/hot-trend-books")
    public ResponseEntity<List<MainHotTrendBookResponse>> getHotTrendBooks(
            @RequestParam(defaultValue = "15") Integer limit
    ) {
        return ResponseEntity.ok(
                mainPageService.getHotTrendBooks(limit)
        );
    }
}
