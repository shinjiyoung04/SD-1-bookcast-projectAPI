package com.example.teamproject1.bookhistory.service;

import com.example.teamproject1.bookhistory.dto.BookViewLogRequest;
import com.example.teamproject1.bookhistory.dto.RecentViewedBookResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookHistoryService {

    private static final int MAX_RECENT_LIMIT = 5;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public List<RecentViewedBookResponse> recordViewAndGetRecent(
            Long userId,
            BookViewLogRequest request,
            Integer limit
    ) {
        validateActiveUser(userId);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "도서 정보가 필요합니다."
            );
        }

        String normalizedIsbn = normalizeIsbn(request.isbn());

        Long bookId = findOrCreateBook(
                normalizedIsbn,
                request
        );

        updateBookMetadata(bookId, request);

        Integer recentDuplicateCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_book_logs
                WHERE user_id = ?
                  AND book_id = ?
                  AND viewed_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 SECOND)
                """,
                Integer.class,
                userId,
                bookId
        );

        if (recentDuplicateCount == null || recentDuplicateCount == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO user_book_logs (
                        user_id,
                        book_id,
                        viewed_at
                    ) VALUES (?, ?, CURRENT_TIMESTAMP)
                    """,
                    userId,
                    bookId
            );

            jdbcTemplate.update(
                    """
                    UPDATE books
                    SET view_count = COALESCE(view_count, 0) + 1,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE book_id = ?
                    """,
                    bookId
            );
        }

        return getRecentViewedBooks(
                userId,
                limit
        );
    }

    public List<RecentViewedBookResponse> getRecentViewedBooks(
            Long userId,
            Integer limit
    ) {
        validateActiveUser(userId);

        int safeLimit = normalizeLimit(limit);

        return jdbcTemplate.query(
                """
                SELECT
                    book.book_id,
                    book.isbn,
                    book.title,
                    book.author,
                    book.publisher,
                    book.thumbnail_url,
                    MAX(history.viewed_at) AS last_viewed_at
                FROM user_book_logs history
                JOIN books book
                  ON book.book_id = history.book_id
                WHERE history.user_id = ?
                GROUP BY
                    book.book_id,
                    book.isbn,
                    book.title,
                    book.author,
                    book.publisher,
                    book.thumbnail_url
                ORDER BY last_viewed_at DESC, book.book_id DESC
                LIMIT ?
                """,
                (resultSet, rowNumber) -> {
                    Timestamp viewedAt = resultSet.getTimestamp("last_viewed_at");

                    return new RecentViewedBookResponse(
                            resultSet.getLong("book_id"),
                            resultSet.getString("isbn"),
                            resultSet.getString("title"),
                            resultSet.getString("author"),
                            resultSet.getString("publisher"),
                            resultSet.getString("thumbnail_url"),
                            viewedAt == null
                                    ? null
                                    : viewedAt.toLocalDateTime()
                    );
                },
                userId,
                safeLimit
        );
    }

    private Long findOrCreateBook(
            String normalizedIsbn,
            BookViewLogRequest request
    ) {
        List<Long> existingIds = jdbcTemplate.query(
                """
                SELECT book_id
                FROM books
                WHERE UPPER(
                    REPLACE(
                        REPLACE(isbn, '-', ''),
                        ' ',
                        ''
                    )
                ) = ?
                ORDER BY book_id ASC
                LIMIT 1
                """,
                (resultSet, rowNumber) -> resultSet.getLong("book_id"),
                normalizedIsbn
        );

        if (!existingIds.isEmpty()) {
            return existingIds.get(0);
        }

        String title = metadataValue(
                request.title(),
                "도서 제목 없음"
        );

        String author = metadataValue(
                request.author(),
                "저자 정보 없음"
        );

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO books (
                        isbn,
                        title,
                        author,
                        publisher,
                        thumbnail_url,
                        view_count,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    normalizedIsbn,
                    title,
                    author,
                    nullableMetadata(request.publisher()),
                    nullableMetadata(request.thumbnailUrl())
            );
        } catch (DuplicateKeyException duplicateKeyException) {
            log.debug(
                    "[BookHistoryService] 동시 도서 등록 감지. 기존 ISBN을 재조회합니다. isbn={}",
                    normalizedIsbn
            );
        }

        List<Long> createdIds = jdbcTemplate.query(
                """
                SELECT book_id
                FROM books
                WHERE UPPER(
                    REPLACE(
                        REPLACE(isbn, '-', ''),
                        ' ',
                        ''
                    )
                ) = ?
                ORDER BY book_id ASC
                LIMIT 1
                """,
                (resultSet, rowNumber) -> resultSet.getLong("book_id"),
                normalizedIsbn
        );

        if (createdIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "도서 조회 기록을 저장할 도서 정보를 생성하지 못했습니다."
            );
        }

        return createdIds.get(0);
    }

    private void updateBookMetadata(
            Long bookId,
            BookViewLogRequest request
    ) {
        String title = nullableMetadata(request.title());
        String author = nullableMetadata(request.author());
        String publisher = nullableMetadata(request.publisher());
        String thumbnailUrl = nullableMetadata(request.thumbnailUrl());

        if ("도서 제목 없음".equals(title)) {
            title = null;
        }

        if ("저자 정보 없음".equals(author)) {
            author = null;
        }

        jdbcTemplate.update(
                """
                UPDATE books
                SET title = COALESCE(?, title),
                    author = COALESCE(?, author),
                    publisher = COALESCE(?, publisher),
                    thumbnail_url = COALESCE(?, thumbnail_url),
                    updated_at = CURRENT_TIMESTAMP
                WHERE book_id = ?
                """,
                title,
                author,
                publisher,
                thumbnailUrl,
                bookId
        );
    }

    private void validateActiveUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "회원 번호가 필요합니다."
            );
        }

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users
                WHERE user_id = ?
                  AND status = 'ACTIVE'
                """,
                Integer.class,
                userId
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "활성 회원 정보를 찾을 수 없습니다."
            );
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return MAX_RECENT_LIMIT;
        }

        return Math.max(
                1,
                Math.min(
                        limit,
                        MAX_RECENT_LIMIT
                )
        );
    }

    private String normalizeIsbn(String isbn) {
        if (!StringUtils.hasText(isbn)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN이 필요합니다."
            );
        }

        String normalized = isbn
                .replaceAll("[^0-9Xx]", "")
                .toUpperCase(Locale.ROOT)
                .trim();

        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 ISBN이 필요합니다."
            );
        }

        return normalized;
    }

    private String metadataValue(
            String value,
            String fallback
    ) {
        String normalized = nullableMetadata(value);

        return normalized == null
                ? fallback
                : normalized;
    }

    private String nullableMetadata(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }
}
