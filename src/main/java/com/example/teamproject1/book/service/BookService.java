package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.BookImportRequestDTO;
import com.example.teamproject1.book.dto.BookResponseDTO;
import com.example.teamproject1.book.entity.Book;
import com.example.teamproject1.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class BookService {

    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public BookResponseDTO getBookByIsbn(String isbn) {
        String normalizedIsbn = normalizeIsbn(isbn);

        if (!StringUtils.hasText(normalizedIsbn)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN이 올바르지 않습니다."
            );
        }

        Book book = bookRepository.findByIsbn(normalizedIsbn)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "내부 DB에 등록된 도서가 없습니다."
                ));

        return BookResponseDTO.fromEntity(book);
    }

    public BookResponseDTO importBook(BookImportRequestDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "등록할 도서 정보가 없습니다."
            );
        }

        String normalizedIsbn = normalizeIsbn(dto.getIsbn());

        if (StringUtils.hasText(normalizedIsbn)) {
            Book existingBook = bookRepository.findByIsbn(normalizedIsbn)
                    .orElse(null);

            if (existingBook != null) {
                return BookResponseDTO.fromEntity(existingBook);
            }
        }

        Book book = Book.builder()
                .title(defaultText(clean(dto.getTitle()), "도서 제목 없음"))
                .author(clean(dto.getAuthor()))
                .publisher(clean(dto.getPublisher()))
                .categoryName(clean(dto.getCategory()))
                .isbn(normalizedIsbn)
                .description(clean(dto.getDescription()))
                .imageUrl(clean(dto.getImageUrl()))
                .totalCount(1)
                .availableCount(1)
                .loanCount(dto.getLoanCount() == null ? 0 : dto.getLoanCount())
                .build();

        Book savedBook = bookRepository.save(book);

        return BookResponseDTO.fromEntity(savedBook);
    }

    private String normalizeIsbn(String isbn) {
        if (!StringUtils.hasText(isbn)) {
            return null;
        }

        String onlyNumberAndX = isbn.replaceAll("[^0-9Xx]", "")
                .toUpperCase();

        if (onlyNumberAndX.length() >= 13) {
            return onlyNumberAndX.substring(onlyNumberAndX.length() - 13);
        }

        return onlyNumberAndX;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value
                .replaceAll("<[^>]*>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();

        return cleaned.isBlank() ? null : cleaned;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
