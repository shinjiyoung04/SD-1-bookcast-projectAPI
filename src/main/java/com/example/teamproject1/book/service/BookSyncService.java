package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.BookSyncResponse;
import com.example.teamproject1.book.dto.ExternalBookResponse;
import com.example.teamproject1.book.entity.Book;
import com.example.teamproject1.book.entity.Category;
import com.example.teamproject1.book.repository.BookRepository;
import com.example.teamproject1.book.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookSyncService {

    private final Data4LibraryService data4LibraryService;
    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public BookSyncResponse syncBooks(
            String keyword,
            String title,
            String author,
            String isbn13,
            String publisher,
            Integer pageNo,
            Integer pageSize,
            Long categoryId
    ) {
        log.info("[도서 동기화 시작] keyword={}, title={}, author={}, isbn13={}, publisher={}, pageNo={}, pageSize={}, categoryId={}",
                keyword, title, author, isbn13, publisher, pageNo, pageSize, categoryId);

        List<ExternalBookResponse> externalBooks =
                data4LibraryService.searchBooks(
                        keyword,
                        title,
                        author,
                        isbn13,
                        publisher,
                        pageNo,
                        pageSize
                );

        log.info("[도서 동기화] 정보나루 검색 결과 수={}", externalBooks.size());

        if (!externalBooks.isEmpty()) {
            ExternalBookResponse first = externalBooks.get(0);
            log.info("[도서 동기화] 첫 번째 외부 도서 bookname={}, authors={}, isbn13={}, publisher={}",
                    first.getBookname(),
                    first.getAuthors(),
                    first.getIsbn13(),
                    first.getPublisher());
        }

        Category category = null;

        if (categoryId != null) {
            category = categoryRepository.findById(categoryId)
                    .orElse(null);

            log.info("[도서 동기화] categoryId={}, category 존재 여부={}",
                    categoryId, category != null);
        }

        int insertedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (ExternalBookResponse external : externalBooks) {

            log.info("[도서 동기화] 저장 대상 확인 bookname={}, isbn13={}",
                    external.getBookname(), external.getIsbn13());

            if (external.getIsbn13() == null || external.getIsbn13().isBlank()) {
                skippedCount++;
                log.warn("[도서 동기화] ISBN 없음 → 저장 제외 bookname={}", external.getBookname());
                continue;
            }

            if (external.getBookname() == null || external.getBookname().isBlank()) {
                skippedCount++;
                log.warn("[도서 동기화] 도서명 없음 → 저장 제외 isbn13={}", external.getIsbn13());
                continue;
            }

            Book book = bookRepository.findByIsbn(external.getIsbn13())
                    .orElse(null);

            if (book == null) {
                book = new Book();
                book.setIsbn(external.getIsbn13());
                book.setViewCount(0);
                book.setAverageRating(BigDecimal.ZERO);
                book.setCreatedAt(LocalDateTime.now());
                insertedCount++;

                log.info("[도서 동기화] 신규 저장 isbn13={}, title={}",
                        external.getIsbn13(), external.getBookname());
            } else {
                updatedCount++;

                log.info("[도서 동기화] 기존 도서 갱신 bookId={}, isbn13={}, title={}",
                        book.getBookId(), external.getIsbn13(), external.getBookname());
            }

            book.setTitle(external.getBookname());
            book.setAuthor(external.getAuthors());
            book.setPublisher(external.getPublisher());
            book.setPublishedDate(parseYearToDate(external.getPublicationYear()));
            book.setCategory(category);
            book.setDescription("정보나루 도서 검색 API를 통해 저장된 도서입니다.");
            book.setThumbnailUrl(external.getBookImageUrl());
            book.setUpdatedAt(LocalDateTime.now());

            bookRepository.save(book);
        }

        bookRepository.flush();

        log.info("[도서 동기화 완료] requested={}, inserted={}, updated={}, skipped={}",
                externalBooks.size(), insertedCount, updatedCount, skippedCount);

        return new BookSyncResponse(
                externalBooks.size(),
                insertedCount,
                updatedCount,
                skippedCount,
                "정보나루 도서 검색 결과 동기화가 완료되었습니다."
        );
    }

    private LocalDate parseYearToDate(String publicationYear) {
        if (publicationYear == null || publicationYear.isBlank()) {
            return null;
        }

        try {
            String yearOnly = publicationYear.trim().substring(0, 4);
            int year = Integer.parseInt(yearOnly);
            return LocalDate.of(year, 1, 1);
        } catch (Exception e) {
            return null;
        }
    }
}