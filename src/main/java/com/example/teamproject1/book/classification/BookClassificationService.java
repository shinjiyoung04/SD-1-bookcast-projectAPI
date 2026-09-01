package com.example.teamproject1.book.classification;

import com.example.teamproject1.book.dto.BookClassificationResponse;
import com.example.teamproject1.book.dto.ExternalBookResponse;
import com.example.teamproject1.book.service.Data4LibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookClassificationService {

    private static final Duration CACHE_DURATION =
            Duration.ofHours(6);

    private final Data4LibraryService
            data4LibraryService;

    private final JdbcTemplate
            jdbcTemplate;

    private final PlatformTransactionManager
            transactionManager;

    private final Map<String, CacheEntry>
            cache =
            new ConcurrentHashMap<>();

    public BookClassificationResponse
    getAndPersistClassification(
            String isbn13
    ) {
        String normalizedIsbn =
                normalizeIsbn(isbn13);

        if (!StringUtils.hasText(normalizedIsbn)) {
            throw new IllegalArgumentException(
                    "ISBN이 비어 있습니다."
            );
        }

        BookClassificationResponse cached =
                getCached(normalizedIsbn);

        if (cached != null) {
            Long categoryId =
                    persistCategory(
                            normalizedIsbn,
                            cached.categoryName()
                    );

            return new BookClassificationResponse(
                    cached.isbn13(),
                    cached.title(),
                    cached.classNo(),
                    cached.className(),
                    categoryId,
                    cached.categoryName(),
                    cached.loanCount(),
                    cached.source()
            );
        }

        ExternalBookResponse externalBook =
                data4LibraryService
                        .findBookByIsbn(
                                normalizedIsbn
                        );

        if (externalBook == null) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "정보나루에서 ISBN에 해당하는 도서를 찾을 수 없습니다."
            );
        }

        String categoryName =
                KdcCategoryMapper.resolve(
                        externalBook.getClassNo(),
                        externalBook.getClassName()
                );

        if (!StringUtils.hasText(categoryName)) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "정보나루 도서에 분류번호가 등록되어 있지 않습니다."
            );
        }

        Long categoryId =
                persistCategory(
                        normalizedIsbn,
                        categoryName
                );

        BookClassificationResponse response =
                new BookClassificationResponse(
                        normalizedIsbn,
                        externalBook.getBookname(),
                        externalBook.getClassNo(),
                        externalBook.getClassName(),
                        categoryId,
                        categoryName,
                        externalBook.getLoanCount(),
                        "DATA4LIBRARY"
                );

        cache.put(
                normalizedIsbn,
                new CacheEntry(
                        response,
                        LocalDateTime.now()
                )
        );

        return response;
    }

    public boolean enrichMissingCategories(
            Collection<String> isbns
    ) {
        if (isbns == null || isbns.isEmpty()) {
            return false;
        }

        LinkedHashSet<String> uniqueIsbns =
                new LinkedHashSet<>();

        for (String isbn : isbns) {
            String normalized =
                    normalizeIsbn(isbn);

            if (StringUtils.hasText(normalized)) {
                uniqueIsbns.add(normalized);
            }

            if (uniqueIsbns.size() >= 10) {
                break;
            }
        }

        boolean changed = false;

        for (String isbn : uniqueIsbns) {
            try {
                BookClassificationResponse response =
                        getAndPersistClassification(isbn);

                if (response.categoryId() != null) {
                    changed = true;
                }
            } catch (Exception exception) {
                log.warn(
                        "[도서 카테고리 자동 보정 실패] isbn={}, message={}",
                        isbn,
                        exception.getMessage()
                );
            }
        }

        return changed;
    }

    private BookClassificationResponse
    getCached(
            String isbn
    ) {
        CacheEntry entry =
                cache.get(isbn);

        if (entry == null) {
            return null;
        }

        if (
                entry.cachedAt()
                        .plus(CACHE_DURATION)
                        .isBefore(LocalDateTime.now())
        ) {
            cache.remove(isbn);
            return null;
        }

        return entry.response();
    }

    private Long persistCategory(
            String normalizedIsbn,
            String categoryName
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        transactionTemplate.setPropagationBehavior(
                TransactionDefinition
                        .PROPAGATION_REQUIRES_NEW
        );

        return transactionTemplate.execute(
                status -> {
                    try {
                        Long categoryId =
                                findOrCreateCategory(
                                        categoryName
                                );

                        if (categoryId == null) {
                            return null;
                        }

                        jdbcTemplate.update(
                                """
                                UPDATE books
                                SET
                                    category_id = ?,
                                    updated_at = CURRENT_TIMESTAMP
                                WHERE category_id IS NULL
                                  AND REPLACE(
                                          REPLACE(
                                              UPPER(isbn),
                                              '-',
                                              ''
                                          ),
                                          ' ',
                                          ''
                                      ) = ?
                                """,
                                categoryId,
                                normalizedIsbn
                        );

                        jdbcTemplate.update(
                                """
                                UPDATE hope_applications
                                SET category_id = ?
                                WHERE category_id IS NULL
                                  AND REPLACE(
                                          REPLACE(
                                              UPPER(isbn),
                                              '-',
                                              ''
                                          ),
                                          ' ',
                                          ''
                                      ) = ?
                                """,
                                categoryId,
                                normalizedIsbn
                        );

                        return categoryId;
                    } catch (DataAccessException exception) {
                        log.error(
                                "[도서 카테고리 DB 동기화 실패] isbn={}, category={}",
                                normalizedIsbn,
                                categoryName,
                                exception
                        );

                        status.setRollbackOnly();
                        return null;
                    }
                }
        );
    }

    private Long findOrCreateCategory(
            String categoryName
    ) {
        if (!StringUtils.hasText(categoryName)) {
            return null;
        }

        List<Long> existingIds =
                jdbcTemplate.query(
                        """
                        SELECT category_id
                        FROM categories
                        WHERE category_name = ?
                          AND parent_id IS NULL
                        ORDER BY category_id ASC
                        LIMIT 1
                        """,
                        (
                                resultSet,
                                rowNumber
                        ) ->
                                resultSet.getLong(
                                        "category_id"
                                ),
                        categoryName
                );

        if (!existingIds.isEmpty()) {
            return existingIds.get(0);
        }

        jdbcTemplate.update(
                """
                INSERT INTO categories (
                    category_name,
                    parent_id,
                    created_at
                )
                SELECT
                    ?,
                    NULL,
                    CURRENT_TIMESTAMP
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM categories
                    WHERE category_name = ?
                      AND parent_id IS NULL
                )
                """,
                categoryName,
                categoryName
        );

        return jdbcTemplate.queryForObject(
                """
                SELECT category_id
                FROM categories
                WHERE category_name = ?
                  AND parent_id IS NULL
                ORDER BY category_id ASC
                LIMIT 1
                """,
                Long.class,
                categoryName
        );
    }

    private String normalizeIsbn(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("[^0-9Xx]", "")
                .toUpperCase();
    }

    private record CacheEntry(
            BookClassificationResponse response,
            LocalDateTime cachedAt
    ) {
    }
}
