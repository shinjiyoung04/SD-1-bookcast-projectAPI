package com.example.teamproject1.book.controller;

import com.example.teamproject1.book.dto.ExternalBookExistResponse;
import com.example.teamproject1.book.service.Data4LibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/external/books")
public class ExternalBookExistController {

    private final Data4LibraryService data4LibraryService;

    // 특정 도서관의 도서 소장 여부 및 대출 가능 여부 확인

    @GetMapping("/exist")
    public ResponseEntity<ExternalBookExistResponse> checkBookExist(
            @RequestParam String libCode,
            @RequestParam String isbn13
    ) {
        ExternalBookExistResponse response =
                data4LibraryService.checkBookExist(
                        libCode,
                        isbn13
                );

        return ResponseEntity.ok(response);
    }
}