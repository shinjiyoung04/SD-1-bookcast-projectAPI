package com.example.teamproject1.book.controller;

import com.example.teamproject1.book.classification.BookClassificationService;
import com.example.teamproject1.book.dto.BookClassificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/external/books")
public class ExternalBookClassificationController {

    private final BookClassificationService
            bookClassificationService;

    @GetMapping("/{isbn13}/classification")
    public ResponseEntity<BookClassificationResponse>
    getClassification(
            @PathVariable String isbn13
    ) {
        return ResponseEntity.ok(
                bookClassificationService
                        .getAndPersistClassification(
                                isbn13
                        )
        );
    }
}
