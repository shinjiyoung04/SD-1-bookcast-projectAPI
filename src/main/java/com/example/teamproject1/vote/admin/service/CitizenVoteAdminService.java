package com.example.teamproject1.vote.admin.service;

import com.example.teamproject1.vote.admin.dto.CitizenVoteAdminDtos;
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
public class CitizenVoteAdminService {

    private static final Set<String> ADMIN_ROLES =
            Set.of("ADMIN", "MASTER_ADMIN");

    private static final Set<String> ALLOWED_SORTS =
            Set.of("POPULAR", "TRENDING", "LATEST");

    private static final Set<String> ALLOWED_STATUSES =
            Set.of(
                    "PENDING",
                    "REVIEWING",
                    "APPROVED",
                    "REJECTED",
                    "CANCELED"
            );

    private final JdbcTemplate jdbcTemplate;

    public CitizenVoteAdminDtos.PageResponse getApplications(
            Long requesterUserId,
            String keyword,
            String status,
            String sort,
            Long requestedLibraryId,
            Integer page,
            Integer pageSize
    ) {
        AdminScope scope = resolveAdminScope(requesterUserId);

        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null
                ? 9
                : Math.max(1, Math.min(pageSize, 30));

        String normalizedStatus = normalizeStatus(status);
        String normalizedSort = normalizeSort(sort);

        List<Object> whereParams = new ArrayList<>();
        StringBuilder where =
                new StringBuilder(
                        """
                        WHERE COALESCE(
                                  a.is_hidden,
                                  FALSE
                              ) = FALSE
                        """
                );

        Long effectiveLibraryId = null;

        if ("ADMIN".equals(scope.role())) {
            effectiveLibraryId =
                    scope.managedLibraryId();

            appendManagedLibraryScope(
                    where,
                    whereParams,
                    scope
            );
        } else if (requestedLibraryId != null) {
            validateLibraryExists(requestedLibraryId);
            effectiveLibraryId = requestedLibraryId;
            where.append(" AND a.library_id = ? ");
            whereParams.add(requestedLibraryId);
        }

        if (normalizedStatus != null) {
            where.append(" AND UPPER(a.status) = ? ");
            whereParams.add(normalizedStatus);
        }

        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";

            where.append(
                    """
                    AND (
                        a.title LIKE ?
                        OR a.author LIKE ?
                        OR COALESCE(b.publisher, '') LIKE ?
                        OR a.isbn LIKE ?
                        OR COALESCE(l.library_name, '') LIKE ?
                        OR COALESCE(u.name, '') LIKE ?
                        OR COALESCE(u.login_id, '') LIKE ?
                    )
                    """
            );

            for (int index = 0; index < 7; index++) {
                whereParams.add(like);
            }
        }

        try {
            Aggregate aggregate = loadAggregate(
                    where.toString(),
                    whereParams
            );

            int totalPages = aggregate.totalElements() == 0
                    ? 0
                    : (int) Math.ceil(
                            (double) aggregate.totalElements() / safePageSize
                    );

            int offset = (safePage - 1) * safePageSize;

            String orderBy = switch (normalizedSort) {
                case "LATEST" ->
                        " ORDER BY a.created_at DESC, a.application_id DESC ";
                case "TRENDING" ->
                        " ORDER BY recent_vote_count_7d DESC, vote_count DESC, a.created_at DESC ";
                case "POPULAR" ->
                        " ORDER BY vote_count DESC, recent_vote_count_7d DESC, a.created_at DESC ";
                default -> throw new IllegalStateException(
                        "지원하지 않는 정렬 방식입니다."
                );
            };

            String listSql = baseSelectSql()
                    + where
                    + orderBy
                    + " LIMIT ? OFFSET ? ";

            List<Object> listParams = new ArrayList<>(whereParams);
            listParams.add(safePageSize);
            listParams.add(offset);

            List<CitizenVoteAdminDtos.ItemResponse> content =
                    jdbcTemplate.query(
                            listSql,
                            this::mapItem,
                            listParams.toArray()
                    );

            String scopeLabel = buildScopeLabel(scope, effectiveLibraryId);

            return new CitizenVoteAdminDtos.PageResponse(
                    content,
                    safePage,
                    safePageSize,
                    aggregate.totalElements(),
                    totalPages,
                    aggregate.totalVotes(),
                    aggregate.recentVotes7d(),
                    scope.role(),
                    scope.managedLibraryId(),
                    scope.managedLibraryName(),
                    scopeLabel
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.error(
                    "[CitizenVoteAdminService] 관리자 시민투표 목록 조회 실패. requesterUserId={}",
                    requesterUserId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "관리자 시민투표 현황 조회 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    public CitizenVoteAdminDtos.DetailResponse getApplicationDetail(
            Long requesterUserId,
            Long applicationId
    ) {
        if (applicationId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "신청 번호가 필요합니다."
            );
        }

        AdminScope scope = resolveAdminScope(requesterUserId);

        StringBuilder where = new StringBuilder(
                " WHERE a.application_id = ? "
        );

        List<Object> parameters = new ArrayList<>();
        parameters.add(applicationId);

        if ("ADMIN".equals(scope.role())) {
            appendManagedLibraryScope(
                    where,
                    parameters,
                    scope
            );
        }

        String sql =
                """
                SELECT
                    a.application_id,
                    a.title,
                    a.author,
                    b.publisher,
                    a.isbn,
                    b.published_date,
                    a.library_id,
                    l.library_name,
                    a.user_id AS applicant_user_id,
                    u.name AS applicant_name,
                    u.login_id AS applicant_login_id,
                    u.email AS applicant_email,
                    a.reason,
                    a.status,
                    a.created_at,
                    COALESCE(vote_summary.vote_count, 0) AS vote_count,
                    COALESCE(
                        vote_summary.recent_vote_count_7d,
                        0
                    ) AS recent_vote_count_7d
                FROM hope_applications a
                LEFT JOIN books b
                  ON b.book_id = a.book_id
                LEFT JOIN libraries l
                  ON l.library_id = a.library_id
                LEFT JOIN users u
                  ON u.user_id = a.user_id
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
                                    ) THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS recent_vote_count_7d
                    FROM hope_votes
                    WHERE active = TRUE
                    GROUP BY application_id
                ) vote_summary
                  ON vote_summary.application_id = a.application_id
                """
                        + where;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    this::mapDetail,
                    parameters.toArray()
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 권한이 없거나 희망도서 신청을 찾을 수 없습니다."
            );
        } catch (DataAccessException exception) {
            log.error(
                    "[CitizenVoteAdminService] 관리자 신청 상세 조회 실패. requesterUserId={}, applicationId={}",
                    requesterUserId,
                    applicationId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "희망도서 신청 상세 조회 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    public List<CitizenVoteAdminDtos.LibraryOptionResponse> getLibraryOptions(
            Long requesterUserId
    ) {
        AdminScope scope = resolveAdminScope(requesterUserId);

        if (!"MASTER_ADMIN".equals(scope.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "도서관 전체 목록은 최고 관리자만 조회할 수 있습니다."
            );
        }

        return jdbcTemplate.query(
                """
                SELECT
                    library_id,
                    library_name
                FROM libraries
                ORDER BY library_name ASC, library_id ASC
                """,
                (resultSet, rowNumber) ->
                        new CitizenVoteAdminDtos.LibraryOptionResponse(
                                resultSet.getLong("library_id"),
                                resultSet.getString("library_name")
                        )
        );
    }

    private Aggregate loadAggregate(
            String where,
            List<Object> parameters
    ) {
        String sql =
                """
                SELECT
                    COUNT(*) AS total_elements,
                    COALESCE(
                        SUM(COALESCE(vote_summary.vote_count, 0)),
                        0
                    ) AS total_votes,
                    COALESCE(
                        SUM(
                            COALESCE(
                                vote_summary.recent_vote_count_7d,
                                0
                            )
                        ),
                        0
                    ) AS recent_votes_7d
                FROM hope_applications a
                LEFT JOIN books b
                  ON b.book_id = a.book_id
                LEFT JOIN libraries l
                  ON l.library_id = a.library_id
                LEFT JOIN users u
                  ON u.user_id = a.user_id
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
                                    ) THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS recent_vote_count_7d
                    FROM hope_votes
                    WHERE active = TRUE
                    GROUP BY application_id
                ) vote_summary
                  ON vote_summary.application_id = a.application_id
                """
                        + where;

        return jdbcTemplate.queryForObject(
                sql,
                (resultSet, rowNumber) ->
                        new Aggregate(
                                resultSet.getLong("total_elements"),
                                resultSet.getLong("total_votes"),
                                resultSet.getLong("recent_votes_7d")
                        ),
                parameters.toArray()
        );
    }

    private String baseSelectSql() {
        return """
                SELECT
                    a.application_id,
                    a.title,
                    a.author,
                    b.publisher,
                    a.isbn,
                    b.published_date,
                    a.library_id,
                    l.library_name,
                    a.user_id AS applicant_user_id,
                    u.name AS applicant_name,
                    u.login_id AS applicant_login_id,
                    a.status,
                    a.created_at,
                    COALESCE(vote_summary.vote_count, 0) AS vote_count,
                    COALESCE(
                        vote_summary.recent_vote_count_7d,
                        0
                    ) AS recent_vote_count_7d
                FROM hope_applications a
                LEFT JOIN books b
                  ON b.book_id = a.book_id
                LEFT JOIN libraries l
                  ON l.library_id = a.library_id
                LEFT JOIN users u
                  ON u.user_id = a.user_id
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
                                    ) THEN 1
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS recent_vote_count_7d
                    FROM hope_votes
                    WHERE active = TRUE
                    GROUP BY application_id
                ) vote_summary
                  ON vote_summary.application_id = a.application_id
                """;
    }

    private CitizenVoteAdminDtos.ItemResponse mapItem(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        long voteCount = resultSet.getLong("vote_count");
        long recentVoteCount7d =
                resultSet.getLong("recent_vote_count_7d");
        LocalDateTime createdAt = toLocalDateTime(
                resultSet.getTimestamp("created_at")
        );

        return new CitizenVoteAdminDtos.ItemResponse(
                resultSet.getLong("application_id"),
                resultSet.getString("title"),
                resultSet.getString("author"),
                resultSet.getString("publisher"),
                resultSet.getString("isbn"),
                toLocalDate(resultSet.getDate("published_date")),
                nullableLong(resultSet, "library_id"),
                resultSet.getString("library_name"),
                nullableLong(resultSet, "applicant_user_id"),
                resultSet.getString("applicant_name"),
                resultSet.getString("applicant_login_id"),
                resultSet.getString("status"),
                createdAt,
                voteCount,
                recentVoteCount7d,
                calculatePopularityIndex(
                        voteCount,
                        recentVoteCount7d,
                        createdAt
                )
        );
    }

    private CitizenVoteAdminDtos.DetailResponse mapDetail(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        long voteCount = resultSet.getLong("vote_count");
        long recentVoteCount7d =
                resultSet.getLong("recent_vote_count_7d");
        LocalDateTime createdAt = toLocalDateTime(
                resultSet.getTimestamp("created_at")
        );

        return new CitizenVoteAdminDtos.DetailResponse(
                resultSet.getLong("application_id"),
                resultSet.getString("title"),
                resultSet.getString("author"),
                resultSet.getString("publisher"),
                resultSet.getString("isbn"),
                toLocalDate(resultSet.getDate("published_date")),
                nullableLong(resultSet, "library_id"),
                resultSet.getString("library_name"),
                nullableLong(resultSet, "applicant_user_id"),
                resultSet.getString("applicant_name"),
                resultSet.getString("applicant_login_id"),
                resultSet.getString("applicant_email"),
                resultSet.getString("reason"),
                resultSet.getString("status"),
                createdAt,
                voteCount,
                recentVoteCount7d,
                calculatePopularityIndex(
                        voteCount,
                        recentVoteCount7d,
                        createdAt
                )
        );
    }

    private AdminScope resolveAdminScope(Long requesterUserId) {
        if (requesterUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        try {
            AdminScope scope = jdbcTemplate.queryForObject(
                    """
                    SELECT
                        u.user_id,
                        UPPER(
                            REPLACE(
                                CAST(u.role AS CHAR),
                                'ROLE_',
                                ''
                            )
                        ) AS normalized_role,
                        u.managed_library_id,

                        COALESCE(
                            NULLIF(
                                TRIM(
                                    u.managed_library_code
                                ),
                                ''
                            ),
                            NULLIF(
                                TRIM(
                                    l.lib_code
                                ),
                                ''
                            )
                        ) AS managed_library_code,

                        l.library_name AS managed_library_name
                    FROM users u
                    LEFT JOIN libraries l
                      ON l.library_id = u.managed_library_id
                    WHERE u.user_id = ?
                      AND u.status = 'ACTIVE'
                    """,
                    (resultSet, rowNumber) ->
                            new AdminScope(
                                    resultSet.getLong("user_id"),
                                    resultSet.getString("normalized_role"),
                                    nullableLong(
                                            resultSet,
                                            "managed_library_id"
                                    ),

                                    resultSet.getString(
                                            "managed_library_code"
                                    ),

                                    resultSet.getString(
                                            "managed_library_name"
                                    )
                            ),
                    requesterUserId
            );

            if (scope == null || !ADMIN_ROLES.contains(scope.role())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "관리자만 시민투표 관리 현황을 조회할 수 있습니다."
                );
            }

            return scope;
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "유효한 관리자 계정을 찾을 수 없습니다."
            );
        }
    }

    /**
     * 일반 관리자의 담당 도서관 범위를 적용합니다.
     *
     * 도서관 중복 행 정리 과정에서 관리자 계정과 신청이
     * 서로 다른 library_id를 참조하는 경우가 있으므로,
     * 다음 순서로 같은 도서관을 판별합니다.
     *
     * 1. library_id
     * 2. 정보나루 lib_code
     * 3. 같은 도서관명
     */
    private void appendManagedLibraryScope(
            StringBuilder where,
            List<Object> parameters,
            AdminScope scope
    ) {
        boolean hasLibraryId =
                scope.managedLibraryId()
                        != null;

        boolean hasLibraryCode =
                StringUtils.hasText(
                        scope.managedLibraryCode()
                );

        boolean hasLibraryName =
                StringUtils.hasText(
                        scope.managedLibraryName()
                );

        if (
                !hasLibraryId
                        && !hasLibraryCode
                        && !hasLibraryName
        ) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "담당 도서관이 지정되지 않은 관리자입니다."
            );
        }

        List<String> conditions =
                new ArrayList<>();

        if (hasLibraryId) {
            conditions.add(
                    "a.library_id = ?"
            );

            parameters.add(
                    scope.managedLibraryId()
            );
        }

        if (hasLibraryCode) {
            conditions.add(
                    "TRIM(COALESCE(l.lib_code, '')) = ?"
            );

            parameters.add(
                    scope.managedLibraryCode()
                            .trim()
            );
        }

        if (hasLibraryName) {
            conditions.add(
                    "TRIM(COALESCE(l.library_name, '')) = ?"
            );

            parameters.add(
                    scope.managedLibraryName()
                            .trim()
            );
        }

        where.append(
                " AND ("
        );

        where.append(
                String.join(
                        " OR ",
                        conditions
                )
        );

        where.append(
                ") "
        );
    }

    private void validateLibraryExists(Long libraryId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM libraries WHERE library_id = ?",
                Integer.class,
                libraryId
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "선택한 도서관을 찾을 수 없습니다."
            );
        }
    }

    private String buildScopeLabel(
            AdminScope scope,
            Long effectiveLibraryId
    ) {
        if ("ADMIN".equals(scope.role())) {
            return StringUtils.hasText(scope.managedLibraryName())
                    ? scope.managedLibraryName()
                    : "담당 도서관";
        }

        if (effectiveLibraryId == null) {
            return "전체 도서관";
        }

        String libraryName = jdbcTemplate.queryForObject(
                "SELECT library_name FROM libraries WHERE library_id = ?",
                String.class,
                effectiveLibraryId
        );

        return StringUtils.hasText(libraryName)
                ? libraryName
                : "선택한 도서관";
    }

    private String normalizeSort(String sort) {
        String normalized = StringUtils.hasText(sort)
                ? sort.trim().toUpperCase(Locale.ROOT)
                : "POPULAR";

        return ALLOWED_SORTS.contains(normalized)
                ? normalized
                : "POPULAR";
    }

    private String normalizeStatus(
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

        if (
                "CANCELLED".equals(
                        normalized
                )
        ) {
            normalized = "CANCELED";
        }

        if (!ALLOWED_STATUSES.contains(
                normalized
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PENDING, REVIEWING, APPROVED, REJECTED, CANCELED 상태만 조회할 수 있습니다."
            );
        }

        return normalized;
    }

    private double calculatePopularityIndex(
            long voteCount,
            long recentVoteCount7d,
            LocalDateTime createdAt
    ) {
        if (createdAt == null) {
            return voteCount;
        }

        long ageHours = Math.max(
                1,
                Duration.between(
                        createdAt,
                        LocalDateTime.now()
                ).toHours()
        );

        double ageDays = Math.max(1.0, ageHours / 24.0);
        double score = (voteCount + recentVoteCount7d * 1.5)
                / Math.sqrt(ageDays);

        return Math.round(score * 100.0) / 100.0;
    }

    private Long nullableLong(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record AdminScope(
            Long userId,
            String role,
            Long managedLibraryId,
            String managedLibraryCode,
            String managedLibraryName
    ) {
    }

    private record Aggregate(
            long totalElements,
            long totalVotes,
            long recentVotes7d
    ) {
    }
}
