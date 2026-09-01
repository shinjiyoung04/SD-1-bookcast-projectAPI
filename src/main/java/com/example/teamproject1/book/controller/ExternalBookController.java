package com.example.teamproject1.book.controller;

import com.example.teamproject1.book.dto.NaverBookResponse;
import com.example.teamproject1.book.service.CombinedBookSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/external/books")
public class ExternalBookController {

    private final CombinedBookSearchService combinedBookSearchService;


    @GetMapping
    public ResponseEntity<List<NaverBookResponse>> searchBooks(
            @RequestParam(defaultValue = "ALL") String provider,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String author,
            @RequestParam(defaultValue = "") String isbn13,
            @RequestParam(defaultValue = "") String publisher,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        List<NaverBookResponse> response =
                combinedBookSearchService.searchBooks(
                        provider,
                        keyword,
                        title,
                        author,
                        isbn13,
                        publisher,
                        pageNo,
                        pageSize
                );

        return ResponseEntity.ok(response);
    }
}
