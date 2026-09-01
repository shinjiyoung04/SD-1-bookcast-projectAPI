package com.example.teamproject1.book.controller;

import com.example.teamproject1.book.dto.BookImportRequestDTO;
import com.example.teamproject1.book.dto.BookResponseDTO;
import com.example.teamproject1.book.dto.ExternalBookSearchDTO;
import com.example.teamproject1.book.service.BookService;
import com.example.teamproject1.book.service.ExternalBookSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final ExternalBookSearchService externalBookSearchService;
    private final BookService bookService;

    /**
     * 외부 도서 검색
     *
     * GET /api/books/external-search?keyword=검색어&page=0&size=10
     */
    @GetMapping("/external-search")
    public ResponseEntity<List<ExternalBookSearchDTO>> externalSearch(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
                externalBookSearchService.search(keyword, page, size)
        );
    }

    /**
     * 내부 DB ISBN 기준 도서 상세 조회
     *
     * GET /api/books/isbn/9788936434120
     */
    @GetMapping("/isbn/{isbn}")
    public ResponseEntity<BookResponseDTO> getBookByIsbn(
            @PathVariable String isbn
    ) {
        return ResponseEntity.ok(bookService.getBookByIsbn(isbn));
    }

    /**
     * 외부 검색 결과를 내부 books 테이블에 등록
     *
     * POST /api/books/import
     */
    @PostMapping("/import")
    public ResponseEntity<BookResponseDTO> importBook(
            @RequestBody BookImportRequestDTO dto
    ) {
        return ResponseEntity.ok(bookService.importBook(dto));
    }
}

