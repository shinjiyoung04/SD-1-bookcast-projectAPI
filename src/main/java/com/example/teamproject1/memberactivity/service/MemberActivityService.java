package com.example.teamproject1.memberactivity.service;

import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.BookInteractionRequest;
import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.BookUserStateResponse;
import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.LikedBookResponse;
import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.ReviewCreateRequest;
import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.ReviewResponse;
import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.VotedApplicationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberActivityService {

    private final JdbcTemplate jdbcTemplate;

    public BookUserStateResponse getBookUserState(
            Long userId,
            String isbn
    ) {
        String normalizedIsbn = normalizeIsbn(isbn);

        boolean liked = false;
        boolean wishlisted = false;

        if (userId != null) {
            validateActiveUser(userId);

            liked = exists(
                    """
                    SELECT COUNT(*)
                    FROM book_likes
                    WHERE user_id = ?
                      AND isbn = ?
                    """,
                    userId,
                    normalizedIsbn
            );

            wishlisted = exists(
                    """
                    SELECT COUNT(*)
                    FROM book_wishlists
                    WHERE user_id = ?
                      AND isbn = ?
                    """,
                    userId,
                    normalizedIsbn
            );
        }

        Long totalLikeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM book_likes
                WHERE isbn = ?
                """,
                Long.class,
                normalizedIsbn
        );

        return new BookUserStateResponse(
                normalizedIsbn,
                liked,
                wishlisted,
                totalLikeCount == null ? 0L : totalLikeCount
        );
    }

    @Transactional
    public BookUserStateResponse likeBook(
            Long userId,
            String isbn,
            BookInteractionRequest request
    ) {
        validateActiveUser(userId);
        validateInteractionRequest(request);

        String normalizedIsbn = normalizeIsbn(isbn);

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO book_likes (
                        user_id,
                        isbn,
                        title,
                        author,
                        publisher,
                        thumbnail_url
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        title = VALUES(title),
                        author = VALUES(author),
                        publisher = VALUES(publisher),
                        thumbnail_url = VALUES(thumbnail_url),
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    userId,
                    normalizedIsbn,
                    normalizeRequired(request.title(), "도서명"),
                    normalizeRequired(request.author(), "저자명"),
                    normalizeOptional(request.publisher()),
                    normalizeOptional(request.thumbnailUrl())
            );

            return getBookUserState(userId, normalizedIsbn);
        } catch (DataAccessException exception) {
            log.error(
                    "[MemberActivityService] 도서 좋아요 저장 실패. userId={}, isbn={}",
                    userId,
                    normalizedIsbn,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "도서 좋아요 저장 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    @Transactional
    public BookUserStateResponse unlikeBook(
            Long userId,
            String isbn
    ) {
        validateActiveUser(userId);

        String normalizedIsbn = normalizeIsbn(isbn);

        jdbcTemplate.update(
                """
                DELETE FROM book_likes
                WHERE user_id = ?
                  AND isbn = ?
                """,
                userId,
                normalizedIsbn
        );

        return getBookUserState(userId, normalizedIsbn);
    }

    @Transactional
    public BookUserStateResponse addWishlist(
            Long userId,
            String isbn,
            BookInteractionRequest request
    ) {
        validateActiveUser(userId);
        validateInteractionRequest(request);

        String normalizedIsbn = normalizeIsbn(isbn);

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO book_wishlists (
                        user_id,
                        isbn,
                        title,
                        author,
                        publisher,
                        thumbnail_url
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        title = VALUES(title),
                        author = VALUES(author),
                        publisher = VALUES(publisher),
                        thumbnail_url = VALUES(thumbnail_url),
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    userId,
                    normalizedIsbn,
                    normalizeRequired(request.title(), "도서명"),
                    normalizeRequired(request.author(), "저자명"),
                    normalizeOptional(request.publisher()),
                    normalizeOptional(request.thumbnailUrl())
            );

            return getBookUserState(userId, normalizedIsbn);
        } catch (DataAccessException exception) {
            log.error(
                    "[MemberActivityService] 도서 찜 저장 실패. userId={}, isbn={}",
                    userId,
                    normalizedIsbn,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "도서 찜 저장 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    @Transactional
    public BookUserStateResponse removeWishlist(
            Long userId,
            String isbn
    ) {
        validateActiveUser(userId);

        String normalizedIsbn = normalizeIsbn(isbn);

        jdbcTemplate.update(
                """
                DELETE FROM book_wishlists
                WHERE user_id = ?
                  AND isbn = ?
                """,
                userId,
                normalizedIsbn
        );

        return getBookUserState(userId, normalizedIsbn);
    }

    public List<ReviewResponse> getReviews(String isbn) {
        String normalizedIsbn = normalizeIsbn(isbn);

        return jdbcTemplate.query(
                """
                SELECT
                    review.review_id,
                    review.user_id,
                    COALESCE(
                        NULLIF(user_account.nickname, ''),
                        NULLIF(user_account.name, ''),
                        user_account.login_id,
                        '사용자'
                    ) AS nickname,
                    review.rating,
                    review.content,
                    review.created_at,
                    review.updated_at
                FROM book_reviews review
                JOIN books book
                  ON book.book_id = review.book_id
                JOIN users user_account
                  ON user_account.user_id = review.user_id
                WHERE book.isbn = ?
                  AND review.is_deleted = FALSE
                  AND user_account.status = 'ACTIVE'
                ORDER BY
                    review.created_at DESC,
                    review.review_id DESC
                """,
                (resultSet, rowNumber) -> new ReviewResponse(
                        resultSet.getLong("review_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("nickname"),
                        resultSet.getBigDecimal("rating") == null
                                ? 0
                                : resultSet.getBigDecimal("rating").intValue(),
                        resultSet.getString("content"),
                        toLocalDateTime(resultSet.getTimestamp("created_at")),
                        toLocalDateTime(resultSet.getTimestamp("updated_at"))
                ),
                normalizedIsbn
        );
    }

    @Transactional
    public ReviewResponse createReview(
            Long userId,
            String isbn,
            ReviewCreateRequest request
    ) {
        validateActiveUser(userId);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "리뷰 정보가 필요합니다."
            );
        }

        String normalizedIsbn = normalizeIsbn(isbn);
        String content = normalizeRequired(request.content(), "리뷰 내용");
        String title = normalizeRequired(request.title(), "도서명");
        String author = normalizeRequired(request.author(), "저자명");

        if (request.score() < 1 || request.score() > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "평점은 1점부터 5점까지 입력할 수 있습니다."
            );
        }

        try {
            Long bookId = resolveBookId(
                    normalizedIsbn,
                    title,
                    author,
                    normalizeOptional(request.publisher()),
                    normalizeOptional(request.thumbnailUrl())
            );

            KeyHolder keyHolder = new GeneratedKeyHolder();

            int inserted = jdbcTemplate.update(
                    connection -> {
                        PreparedStatement statement = connection.prepareStatement(
                                """
                                INSERT INTO book_reviews (
                                    book_id,
                                    user_id,
                                    content,
                                    rating,
                                    is_deleted,
                                    created_at,
                                    updated_at
                                )
                                VALUES (?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                                """,
                                Statement.RETURN_GENERATED_KEYS
                        );

                        statement.setLong(1, bookId);
                        statement.setLong(2, userId);
                        statement.setString(3, content);
                        statement.setInt(4, request.score());

                        return statement;
                    },
                    keyHolder
            );

            if (inserted == 0 || keyHolder.getKey() == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "리뷰 저장에 실패했습니다."
                );
            }

            return getReviewById(keyHolder.getKey().longValue());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.error(
                    "[MemberActivityService] 리뷰 저장 실패. userId={}, isbn={}",
                    userId,
                    normalizedIsbn,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "리뷰 저장 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    public List<LikedBookResponse> getLikedBooks(Long userId) {
        validateActiveUser(userId);

        return jdbcTemplate.query(
                """
                SELECT
                    isbn,
                    title,
                    author,
                    publisher,
                    thumbnail_url,
                    created_at
                FROM book_likes
                WHERE user_id = ?
                ORDER BY created_at DESC, like_id DESC
                """,
                (resultSet, rowNumber) -> new LikedBookResponse(
                        resultSet.getString("isbn"),
                        resultSet.getString("title"),
                        resultSet.getString("author"),
                        resultSet.getString("publisher"),
                        resultSet.getString("thumbnail_url"),
                        toLocalDateTime(resultSet.getTimestamp("created_at"))
                ),
                userId
        );
    }

    public List<VotedApplicationResponse> getVotedApplications(Long userId) {
        validateActiveUser(userId);

        return jdbcTemplate.query(
                """
                SELECT
                    application.application_id,
                    application.title,
                    application.author,
                    book.publisher,
                    application.isbn,
                    book.thumbnail_url,
                    library.library_name,
                    application.status,

                    (
                        SELECT COUNT(*)
                        FROM hope_votes vote_count
                        WHERE vote_count.application_id =
                              application.application_id
                          AND vote_count.active = TRUE
                    ) AS vote_count,

                    my_vote.voted_at,
                    application.created_at

                FROM hope_votes my_vote

                JOIN hope_applications application
                  ON application.application_id =
                     my_vote.application_id

                LEFT JOIN books book
                  ON book.book_id =
                     application.book_id

                LEFT JOIN libraries library
                  ON library.library_id =
                     application.library_id

                WHERE my_vote.user_id = ?
                  AND my_vote.active = TRUE
                  AND application.is_hidden = FALSE

                ORDER BY
                    my_vote.voted_at DESC,
                    application.application_id DESC
                """,
                (resultSet, rowNumber) -> new VotedApplicationResponse(
                        resultSet.getLong("application_id"),
                        resultSet.getString("title"),
                        resultSet.getString("author"),
                        resultSet.getString("publisher"),
                        resultSet.getString("isbn"),
                        resultSet.getString("thumbnail_url"),
                        resultSet.getString("library_name"),
                        resultSet.getString("status"),
                        resultSet.getLong("vote_count"),
                        toLocalDateTime(resultSet.getTimestamp("voted_at")),
                        toLocalDateTime(resultSet.getTimestamp("created_at"))
                ),
                userId
        );
    }

    private ReviewResponse getReviewById(Long reviewId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT
                        review.review_id,
                        review.user_id,
                        COALESCE(
                            NULLIF(user_account.nickname, ''),
                            NULLIF(user_account.name, ''),
                            user_account.login_id,
                            '사용자'
                        ) AS nickname,
                        review.rating,
                        review.content,
                        review.created_at,
                        review.updated_at
                    FROM book_reviews review
                    JOIN users user_account
                      ON user_account.user_id = review.user_id
                    WHERE review.review_id = ?
                      AND review.is_deleted = FALSE
                    """,
                    (resultSet, rowNumber) -> new ReviewResponse(
                            resultSet.getLong("review_id"),
                            resultSet.getLong("user_id"),
                            resultSet.getString("nickname"),
                            resultSet.getBigDecimal("rating") == null
                                    ? 0
                                    : resultSet.getBigDecimal("rating").intValue(),
                            resultSet.getString("content"),
                            toLocalDateTime(resultSet.getTimestamp("created_at")),
                            toLocalDateTime(resultSet.getTimestamp("updated_at"))
                    ),
                    reviewId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "저장한 리뷰를 찾을 수 없습니다."
            );
        }
    }

    private Long resolveBookId(
            String isbn,
            String title,
            String author,
            String publisher,
            String thumbnailUrl
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO books (
                    isbn,
                    title,
                    author,
                    publisher,
                    thumbnail_url,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title),
                    author = VALUES(author),
                    publisher = CASE
                        WHEN VALUES(publisher) IS NULL
                          OR VALUES(publisher) = ''
                        THEN publisher
                        ELSE VALUES(publisher)
                    END,
                    thumbnail_url = CASE
                        WHEN VALUES(thumbnail_url) IS NULL
                          OR VALUES(thumbnail_url) = ''
                        THEN thumbnail_url
                        ELSE VALUES(thumbnail_url)
                    END,
                    updated_at = CURRENT_TIMESTAMP
                """,
                isbn,
                title,
                author,
                publisher,
                thumbnailUrl
        );

        Long bookId = jdbcTemplate.queryForObject(
                """
                SELECT book_id
                FROM books
                WHERE isbn = ?
                """,
                Long.class,
                isbn
        );

        if (bookId == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "도서 정보를 저장하지 못했습니다."
            );
        }

        return bookId;
    }

    private boolean exists(
            String sql,
            Object... parameters
    ) {
        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                parameters
        );

        return count != null && count > 0;
    }

    private void validateInteractionRequest(BookInteractionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "도서 정보가 필요합니다."
            );
        }
    }

    private void validateActiveUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
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
                    HttpStatus.UNAUTHORIZED,
                    "활성 상태의 로그인 사용자를 찾을 수 없습니다."
            );
        }
    }

    private String normalizeIsbn(String isbn) {
        if (!StringUtils.hasText(isbn)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN이 필요합니다."
            );
        }

        String normalized = isbn.trim().replace("-", "");

        if (normalized.length() > 20) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN 형식이 올바르지 않습니다."
            );
        }

        return normalized;
    }

    private String normalizeRequired(
            String value,
            String fieldName
    ) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + "을(를) 입력해주세요."
            );
        }

        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value)
                ? value.trim()
                : null;
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null
                ? null
                : value.toLocalDateTime();
    }
}
