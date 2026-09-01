package com.example.teamproject1.bookhistory.controller;

import com.example.teamproject1.bookhistory.dto.BookViewLogRequest;
import com.example.teamproject1.bookhistory.dto.RecentViewedBookResponse;
import com.example.teamproject1.bookhistory.service.BookHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/book-history")
public class BookHistoryController {

    private final BookHistoryService bookHistoryService;

    /**
     * 상세페이지 진입 기록 저장 + 최근 본 도서 반환
     */
    @PostMapping("/view")
    public ResponseEntity<List<RecentViewedBookResponse>> recordView(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") Integer limit,
            @RequestBody BookViewLogRequest request
    ) {
        return ResponseEntity.ok(
                bookHistoryService.recordViewAndGetRecent(
                        userId,
                        request,
                        limit
                )
        );
    }

    /**
     * 로그인 사용자의 최근 본 서로 다른 도서를 최근 순으로 최대 5개 반환
     */
    @GetMapping("/recent")
    public ResponseEntity<List<RecentViewedBookResponse>> getRecentViewedBooks(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return ResponseEntity.ok(
                bookHistoryService.getRecentViewedBooks(
                        userId,
                        limit
                )
        );
    }
}
