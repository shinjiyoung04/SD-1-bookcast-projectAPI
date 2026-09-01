package com.example.teamproject1.vote.service;

import com.example.teamproject1.vote.dto.CitizenVoteItemResponse;
import com.example.teamproject1.vote.dto.CitizenVotePageResponse;
import com.example.teamproject1.vote.dto.CitizenVoteToggleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CitizenVoteService {

    private static final Set<String> VOTABLE_STATUSES =
            Set.of("PENDING");

    private static final Set<String> QUERYABLE_STATUSES =
            Set.of(
                    "PENDING",
                    "REVIEWING",
                    "APPROVED",
                    "REJECTED",
                    "CANCELED"
            );

    private static final Set<String> ALLOWED_SORTS =
            Set.of("POPULAR", "TRENDING", "LATEST");

    private final JdbcTemplate jdbcTemplate;

    public CitizenVotePageResponse getPublicApplications(
            Long userId,
            String keyword,
            String status,
            String sort,
            Integer page,
            Integer pageSize
    ) {
        validateUser(userId);

        int safePage =
                page == null || page < 1
                        ? 1
                        : page;

        int safePageSize =
                pageSize == null
                        ? 12
                        : Math.max(1, Math.min(pageSize, 50));

        String normalizedSort =
                normalizeSort(sort);

        String normalizedStatus =
                normalizeStatusFilter(status);

        List<Object> whereParams =
                new ArrayList<>();

        StringBuilder where =
                new StringBuilder(
                        """
                        WHERE COALESCE(
                                  a.is_hidden,
                                  FALSE
                              ) = FALSE
                          AND UPPER(
                                  CAST(
                                      a.status AS CHAR
                                  )
                              ) = ?
                        """
                );

        whereParams.add(normalizedStatus);

        /*
         * 진행 중 시민투표에서는 본인의 신청을 제외
         * 승인·거절·취소된 과거 기록은 읽기 전용이므로
         * 본인이 신청했던 기록도 확인 가능
         */
        if (
                "PENDING".equals(
                        normalizedStatus
                )
                        || "REVIEWING".equals(
                        normalizedStatus
                )
        ) {
            where.append(
                    " AND a.user_id <> ? "
            );

            whereParams.add(userId);
        }

        if (StringUtils.hasText(keyword)) {
            String likeKeyword =
                    "%" + keyword.trim() + "%";

            where.append(
                    """
                    AND (
                        a.title LIKE ?
                        OR a.author LIKE ?
                        OR COALESCE(b.publisher, '') LIKE ?
                        OR a.isbn LIKE ?
                        OR COALESCE(l.library_name, '') LIKE ?
                    )
                    """
            );

            for (int index = 0; index < 5; index++) {
                whereParams.add(likeKeyword);
            }
        }

        try {

            String countSql =
                    """
                    SELECT COUNT(*)
                    FROM hope_applications a

                    LEFT JOIN books b
                        ON b.book_id = a.book_id

                    LEFT JOIN libraries l
                        ON l.library_id = a.library_id
                    """
                    + where;

            Long count =
                    jdbcTemplate.queryForObject(
                            countSql,
                            Long.class,
                            whereParams.toArray()
                    );

            long totalElements =
                    count == null ? 0L : count;

            int totalPages =
                    totalElements == 0
                            ? 0
                            : (int) Math.ceil(
                                    (double) totalElements
                                            / safePageSize
                            );

            int offset =
                    (safePage - 1)
                            * safePageSize;

            String orderBy =
                    switch (normalizedSort) {
                        case "LATEST" ->
                                """
                                ORDER BY
                                    a.created_at DESC,
                                    a.application_id DESC
                                """;

                        case "TRENDING" ->
                                """
                                ORDER BY
                                    recent_vote_count_7d DESC,
                                    vote_count DESC,
                                    a.created_at DESC
                                """;

                        case "POPULAR" ->
                                """
                                ORDER BY
                                    vote_count DESC,
                                    recent_vote_count_7d DESC,
                                    a.created_at DESC
                                """;

                        default ->
                                throw new IllegalStateException(
                                        "지원하지 않는 정렬 방식입니다."
                                );
                    };

            String listSql =
                    """
                    SELECT
                        a.application_id,
                        a.title,
                        a.author,

                        b.publisher AS publisher,
                        b.published_date AS published_date,

                        a.isbn,

                        l.library_name AS library_name,

                        CAST(
                            a.library_id AS CHAR
                        ) AS lib_code,

                        a.reason,
                        a.status,
                        a.created_at,

                        COALESCE(
                            vote_summary.vote_count,
                            0
                        ) AS vote_count,

                        COALESCE(
                            vote_summary.recent_vote_count_7d,
                            0
                        ) AS recent_vote_count_7d,

                        CASE
                            WHEN my_vote.user_id IS NULL
                            THEN FALSE
                            ELSE TRUE
                        END AS voted_by_me

                    FROM hope_applications a

                    LEFT JOIN books b
                        ON b.book_id = a.book_id

                    LEFT JOIN libraries l
                        ON l.library_id = a.library_id

                    LEFT JOIN (
                        SELECT
                            application_id,

                            COUNT(*) AS vote_count,

                            COALESCE(
                                SUM(
                                    CASE
                                        WHEN voted_at >= DATE_SUB(
                                            CURRENT_TIMESTAMP,
                                            INTERVAL 7 DAY
                                        )
                                        THEN 1
                                        ELSE 0
                                    END
                                ),
                                0
                            ) AS recent_vote_count_7d

                        FROM hope_votes
                        WHERE active = TRUE
                        GROUP BY application_id
                    ) vote_summary
                        ON vote_summary.application_id =
                           a.application_id

                    LEFT JOIN hope_votes my_vote
                        ON my_vote.application_id =
                           a.application_id
                       AND my_vote.user_id = ?
                       AND my_vote.active = TRUE

                    """
                    + where
                    + orderBy
                    + " LIMIT ? OFFSET ? ";

            List<Object> listParams =
                    new ArrayList<>();

            /*
             * my_vote.user_id = ?
             */
            listParams.add(userId);

            /*
             * WHERE a.user_id <> ? 및 검색어
             */
            listParams.addAll(whereParams);

            listParams.add(safePageSize);
            listParams.add(offset);

            List<CitizenVoteItemResponse> content =
                    jdbcTemplate.query(
                            listSql,
                            this::mapItem,
                            listParams.toArray()
                    );

            return new CitizenVotePageResponse(
                    content,
                    safePage,
                    safePageSize,
                    totalElements,
                    totalPages
            );
        } catch (DataAccessException exception) {
            log.error(
                    "[CitizenVoteService] 시민 투표 목록 조회 실패",
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    extractDatabaseMessage(
                            exception,
                            "시민 투표 목록 조회 중 DB 오류가 발생했습니다."
                    ),
                    exception
            );
        }
    }

    @Transactional
    public CitizenVoteToggleResponse toggleVote(
            Long applicationId,
            Long userId
    ) {
        if (applicationId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "신청 번호가 필요합니다."
            );
        }

        validateUser(userId);

        ApplicationMeta application =
                getApplicationMeta(
                        applicationId
                );

        if (application.userId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "본인이 신청한 희망도서에는 투표할 수 없습니다."
            );
        }

        if (!VOTABLE_STATUSES.contains(
                application.status()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "승인 대기 중인 희망도서에만 투표할 수 있습니다."
            );
        }

        try {
            Boolean currentActive =
                    findCurrentVoteState(
                            applicationId,
                            userId
                    );

            boolean nextActive =
                    currentActive == null
                            || !currentActive;

            if (currentActive == null) {
                /*
                 * 최초 투표
                 */
                jdbcTemplate.update(
                        """
                        INSERT INTO hope_votes (
                            application_id,
                            user_id,
                            active,
                            voted_at,
                            canceled_at
                        )
                        VALUES (
                            ?,
                            ?,
                            TRUE,
                            CURRENT_TIMESTAMP,
                            NULL
                        )
                        """,
                        applicationId,
                        userId
                );
            } else if (nextActive) {
                /*
                 * 취소했던 투표를 다시 활성화
                 */
                jdbcTemplate.update(
                        """
                        UPDATE hope_votes
                        SET
                            active = TRUE,
                            voted_at = CURRENT_TIMESTAMP,
                            canceled_at = NULL
                        WHERE application_id = ?
                          AND user_id = ?
                        """,
                        applicationId,
                        userId
                );
            } else {
                /*
                 * 현재 투표 취소
                 */
                jdbcTemplate.update(
                        """
                        UPDATE hope_votes
                        SET
                            active = FALSE,
                            canceled_at = CURRENT_TIMESTAMP
                        WHERE application_id = ?
                          AND user_id = ?
                        """,
                        applicationId,
                        userId
                );
            }

            saveVoteEventIfTableExists(
                    applicationId,
                    userId,
                    nextActive
            );

            VoteCounts counts =
                    getVoteCounts(
                            applicationId
                    );

            return new CitizenVoteToggleResponse(
                    applicationId,
                    nextActive,
                    counts.voteCount(),
                    counts.recentVoteCount7d(),
                    nextActive
                            ? "이 희망도서에 공감했습니다."
                            : "공감 투표를 취소했습니다."
            );
        } catch (DataAccessException exception) {
            log.error(
                    "[CitizenVoteService] 투표 처리 실패. "
                            + "applicationId={}, userId={}",
                    applicationId,
                    userId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    extractDatabaseMessage(
                            exception,
                            "투표 처리 중 DB 오류가 발생했습니다."
                    ),
                    exception
            );
        }
    }

    private CitizenVoteItemResponse mapItem(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        long voteCount =
                resultSet.getLong(
                        "vote_count"
                );

        long recentVoteCount7d =
                resultSet.getLong(
                        "recent_vote_count_7d"
                );

        LocalDateTime createdAt =
                toLocalDateTime(
                        resultSet.getTimestamp(
                                "created_at"
                        )
                );

        return new CitizenVoteItemResponse(
                resultSet.getLong(
                        "application_id"
                ),

                resultSet.getString(
                        "title"
                ),

                resultSet.getString(
                        "author"
                ),

                resultSet.getString(
                        "publisher"
                ),

                resultSet.getString(
                        "isbn"
                ),

                toLocalDate(
                        resultSet.getDate(
                                "published_date"
                        )
                ),

                resultSet.getString(
                        "library_name"
                ),

                resultSet.getString(
                        "lib_code"
                ),

                resultSet.getString(
                        "reason"
                ),

                resultSet.getString(
                        "status"
                ),

                createdAt,
                voteCount,
                recentVoteCount7d,

                resultSet.getBoolean(
                        "voted_by_me"
                ),

                calculatePopularityIndex(
                        voteCount,
                        recentVoteCount7d,
                        createdAt
                )
        );
    }

    private void validateUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        Integer count =
                jdbcTemplate.queryForObject(
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
                    "유효한 로그인 사용자를 찾을 수 없습니다."
            );
        }
    }

    private ApplicationMeta getApplicationMeta(
            Long applicationId
    ) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT
                        user_id,
                        status
                    FROM hope_applications
                    WHERE application_id = ?
                    """,
                    (resultSet, rowNumber) ->
                            new ApplicationMeta(
                                    resultSet.getLong(
                                            "user_id"
                                    ),

                                    normalizeApplicationStatus(
                                            resultSet.getString(
                                                    "status"
                                            )
                                    )
                            ),
                    applicationId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "희망도서 신청을 찾을 수 없습니다."
            );
        }
    }

    private Boolean findCurrentVoteState(
            Long applicationId,
            Long userId
    ) {
        List<Boolean> states =
                jdbcTemplate.query(
                        """
                        SELECT active
                        FROM hope_votes
                        WHERE application_id = ?
                          AND user_id = ?
                        """,
                        (resultSet, rowNumber) ->
                                resultSet.getBoolean(
                                        "active"
                                ),
                        applicationId,
                        userId
                );

        return states.isEmpty()
                ? null
                : states.get(0);
    }

    private VoteCounts getVoteCounts(
            Long applicationId
    ) {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    COUNT(*) AS vote_count,

                    COALESCE(
                        SUM(
                            CASE
                                WHEN voted_at >= DATE_SUB(
                                    CURRENT_TIMESTAMP,
                                    INTERVAL 7 DAY
                                )
                                THEN 1
                                ELSE 0
                            END
                        ),
                        0
                    ) AS recent_vote_count_7d

                FROM hope_votes
                WHERE application_id = ?
                  AND active = TRUE
                """,
                (resultSet, rowNumber) ->
                        new VoteCounts(
                                resultSet.getLong(
                                        "vote_count"
                                ),

                                resultSet.getLong(
                                        "recent_vote_count_7d"
                                )
                        ),
                applicationId
        );
    }

    private void saveVoteEventIfTableExists(
            Long applicationId,
            Long userId,
            boolean active
    ) {
        Integer tableCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'hope_vote_events'
                        """,
                        Integer.class
                );

        if (tableCount == null || tableCount == 0) {
            log.warn(
                    "[CitizenVoteService] hope_vote_events 테이블이 없어 "
                            + "투표 이벤트 이력 저장을 생략합니다."
            );

            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO hope_vote_events (
                    application_id,
                    user_id,
                    event_type
                )
                VALUES (?, ?, ?)
                """,
                applicationId,
                userId,
                active
                        ? "VOTE"
                        : "CANCEL"
        );
    }

    private String normalizeSort(
            String sort
    ) {
        String normalized =
                StringUtils.hasText(sort)
                        ? sort.trim()
                                .toUpperCase(
                                        Locale.ROOT
                                )
                        : "POPULAR";

        return ALLOWED_SORTS.contains(
                normalized
        )
                ? normalized
                : "POPULAR";
    }

    private String normalizeStatusFilter(
            String status
    ) {
        String normalized =
                !StringUtils.hasText(status)
                        || "ALL".equalsIgnoreCase(status)
                        ? "PENDING"
                        : status.trim()
                                .toUpperCase(
                                        Locale.ROOT
                                );

        /*
         * 프론트 또는 과거 데이터에서 영국식 표기인
         * CANCELLED가 넘어와도 DB의 CANCELED로 통일합니다.
         */
        if (
                "CANCELLED".equals(
                        normalized
                )
        ) {
            normalized = "CANCELED";
        }

        if (!QUERYABLE_STATUSES.contains(
                normalized
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PENDING, REVIEWING, APPROVED, REJECTED, CANCELED 상태만 조회할 수 있습니다."
            );
        }

        return normalized;
    }

    private String normalizeApplicationStatus(
            String status
    ) {
        if (status == null) {
            return "";
        }

        return status
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    private double calculatePopularityIndex(
            long voteCount,
            long recentVoteCount7d,
            LocalDateTime createdAt
    ) {
        if (createdAt == null) {
            return voteCount;
        }

        long ageHours =
                Math.max(
                        1,
                        Duration.between(
                                createdAt,
                                LocalDateTime.now()
                        ).toHours()
                );

        double ageDays =
                Math.max(
                        1.0,
                        ageHours / 24.0
                );

        double score =
                (
                        voteCount
                                + recentVoteCount7d * 1.5
                )
                        / Math.sqrt(ageDays);

        return Math.round(
                score * 100.0
        ) / 100.0;
    }

    private String extractDatabaseMessage(
            DataAccessException exception,
            String fallback
    ) {
        Throwable root =
                exception.getMostSpecificCause();

        if (root != null
                && StringUtils.hasText(
                        root.getMessage()
                )) {
            return fallback
                    + " 원인: "
                    + root.getMessage();
        }

        return fallback;
    }

    private LocalDate toLocalDate(
            Date value
    ) {
        return value == null
                ? null
                : value.toLocalDate();
    }

    private LocalDateTime toLocalDateTime(
            Timestamp value
    ) {
        return value == null
                ? null
                : value.toLocalDateTime();
    }

    private record ApplicationMeta(
            Long userId,
            String status
    ) {
    }

    private record VoteCounts(
            long voteCount,
            long recentVoteCount7d
    ) {
    }
}
