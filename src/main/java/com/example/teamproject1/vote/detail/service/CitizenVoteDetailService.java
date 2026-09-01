package com.example.teamproject1.vote.detail.service;

import com.example.teamproject1.vote.detail.dto.CitizenVoteDetailDtos;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CitizenVoteDetailService {

    private static final Set<String> ADMIN_ROLES =
            Set.of("ADMIN", "MASTER_ADMIN");

    private final JdbcTemplate jdbcTemplate;

    public CitizenVoteDetailDtos.DetailResponse getDetail(
            Long requesterUserId,
            Long applicationId
    ) {
        validateApplicationId(applicationId);

        Requester requester = resolveRequester(requesterUserId);

        StringBuilder where = new StringBuilder(
                """
                WHERE application.application_id = ?
                  AND COALESCE(application.is_hidden, FALSE) = FALSE
                """
        );

        /*
         * SQL 본문에서 my_vote.user_id = ?가 WHERE보다 먼저 등장하므로
         * requesterUserId를 첫 번째 파라미터로 넣습니다.
         */
        List<Object> parameters = new ArrayList<>();
        parameters.add(requester.userId());
        parameters.add(applicationId);

        if ("ADMIN".equals(requester.role())) {
            validateManagedLibrary(requester);

            where.append(
                    """
                      AND (
                            application.library_id = ?
                            OR (
                                ? IS NOT NULL
                                AND UPPER(
                                    TRIM(
                                        COALESCE(
                                            library.lib_code,
                                            ''
                                        )
                                    )
                                ) = UPPER(TRIM(?))
                            )
                            OR (
                                ? IS NOT NULL
                                AND REPLACE(
                                    UPPER(
                                        TRIM(
                                            COALESCE(
                                                library.library_name,
                                                ''
                                            )
                                        )
                                    ),
                                    ' ',
                                    ''
                                ) = REPLACE(
                                    UPPER(TRIM(?)),
                                    ' ',
                                    ''
                                )
                            )
                      )
                    """
            );

            parameters.add(requester.managedLibraryId());
            parameters.add(requester.managedLibraryCode());
            parameters.add(requester.managedLibraryCode());
            parameters.add(requester.managedLibraryName());
            parameters.add(requester.managedLibraryName());
        }

        String sql =
                """
                SELECT
                    application.application_id,
                    application.user_id AS applicant_user_id,

                    COALESCE(
                        NULLIF(TRIM(applicant.nickname), ''),
                        NULLIF(TRIM(applicant.name), ''),
                        applicant.login_id
                    ) AS applicant_display_name,

                    application.title,
                    application.author,

                    COALESCE(
                        NULLIF(TRIM(book_by_id.publisher), ''),
                        NULLIF(TRIM(book_by_isbn.publisher), '')
                    ) AS publisher,

                    application.isbn,

                    COALESCE(
                        NULLIF(TRIM(book_by_id.thumbnail_url), ''),
                        NULLIF(TRIM(book_by_isbn.thumbnail_url), '')
                    ) AS thumbnail_url,

                    COALESCE(
                        book_by_id.published_date,
                        book_by_isbn.published_date
                    ) AS published_date,

                    application.library_id,
                    library.library_name,

                    application.reason,
                    application.status,
                    application.admin_comment,
                    application.created_at,
                    application.processed_at,

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
                    END AS voted_by_me,

                    prediction.prediction_id,
                    prediction.approval_probability,
                    prediction.popularity_score,
                    prediction.vote_adjustment,
                    prediction.final_score,
                    prediction.model_version,
                    prediction.created_at AS predicted_at

                FROM hope_applications application

                LEFT JOIN users applicant
                  ON applicant.user_id = application.user_id

                /*
                 * 1순위: hope_applications.book_id로 연결된 도서
                 */
                LEFT JOIN books book_by_id
                  ON book_by_id.book_id = application.book_id

                /*
                 * 2순위: 기존 신청처럼 book_id가 NULL이거나,
                 * 연결된 books 행에 썸네일이 없는 경우를 대비해
                 * 정규화한 ISBN으로 가장 적절한 books 행을 찾습니다.
                 */
                LEFT JOIN books book_by_isbn
                  ON book_by_isbn.book_id = (
                      SELECT isbn_book.book_id
                      FROM books isbn_book
                      WHERE UPPER(
                                REPLACE(
                                    REPLACE(isbn_book.isbn, '-', ''),
                                    ' ',
                                    ''
                                )
                            )
                            =
                            UPPER(
                                REPLACE(
                                    REPLACE(application.isbn, '-', ''),
                                    ' ',
                                    ''
                                )
                            )
                      ORDER BY
                          CASE
                              WHEN NULLIF(
                                  TRIM(isbn_book.thumbnail_url),
                                  ''
                              ) IS NULL
                              THEN 1
                              ELSE 0
                          END,
                          CASE
                              WHEN isbn_book.book_id =
                                   application.book_id
                              THEN 0
                              ELSE 1
                          END,
                          isbn_book.updated_at DESC,
                          isbn_book.book_id DESC
                      LIMIT 1
                  )

                LEFT JOIN libraries library
                  ON library.library_id = application.library_id

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
                     application.application_id

                LEFT JOIN hope_votes my_vote
                  ON my_vote.application_id =
                     application.application_id
                 AND my_vote.user_id = ?
                 AND my_vote.active = TRUE

                LEFT JOIN ai_predictions prediction
                  ON prediction.prediction_id = (
                      SELECT MAX(latest_prediction.prediction_id)
                      FROM ai_predictions latest_prediction
                      WHERE latest_prediction.application_id =
                            application.application_id
                  )
                """
                        + where;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    (resultSet, rowNumber) ->
                            mapDetail(resultSet, requester),
                    parameters.toArray()
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 권한이 없거나 희망도서 신청을 찾을 수 없습니다."
            );
        } catch (DataAccessException exception) {
            log.error(
                    "[CitizenVoteDetailService] 시민투표 상세 조회 실패. requesterUserId={}, applicationId={}",
                    requesterUserId,
                    applicationId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "시민투표 상세정보 조회 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    @Transactional
    public CitizenVoteDetailDtos.DetailResponse cancelApplication(
            Long requesterUserId,
            Long applicationId
    ) {
        validateApplicationId(applicationId);
        Requester requester = resolveRequester(requesterUserId);

        ApplicationOwner owner;

        try {
            owner = jdbcTemplate.queryForObject(
                    """
                    SELECT
                        user_id,
                        UPPER(CAST(status AS CHAR)) AS normalized_status
                    FROM hope_applications
                    WHERE application_id = ?
                      AND COALESCE(is_hidden, FALSE) = FALSE
                    """,
                    (resultSet, rowNumber) ->
                            new ApplicationOwner(
                                    resultSet.getLong("user_id"),
                                    normalizeStatus(
                                            resultSet.getString(
                                                    "normalized_status"
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

        if (!requester.userId().equals(owner.userId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "신청자 본인만 희망도서 신청을 취소할 수 있습니다."
            );
        }

        if (!"PENDING".equals(owner.status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "승인 대기 상태의 신청만 취소할 수 있습니다."
            );
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE hope_applications
                SET status = 'CANCELED',
                    processed_at = CURRENT_TIMESTAMP
                WHERE application_id = ?
                  AND user_id = ?
                  AND status = 'PENDING'
                  AND COALESCE(is_hidden, FALSE) = FALSE
                """,
                applicationId,
                requester.userId()
        );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "신청 상태가 변경되어 취소하지 못했습니다. 다시 조회해주세요."
            );
        }

        return getDetail(requesterUserId, applicationId);
    }

    private CitizenVoteDetailDtos.DetailResponse mapDetail(
            ResultSet resultSet,
            Requester requester
    ) throws SQLException {
        Long applicantUserId = nullableLong(
                resultSet,
                "applicant_user_id"
        );

        boolean owner = applicantUserId != null
                && applicantUserId.equals(requester.userId());

        String status = normalizeStatus(
                resultSet.getString("status")
        );

        long voteCount = resultSet.getLong("vote_count");
        long recentVoteCount7d = resultSet.getLong(
                "recent_vote_count_7d"
        );

        LocalDateTime createdAt = toLocalDateTime(
                resultSet.getTimestamp("created_at")
        );

        Long predictionId = nullableLong(
                resultSet,
                "prediction_id"
        );

        CitizenVoteDetailDtos.PredictionResponse prediction =
                new CitizenVoteDetailDtos.PredictionResponse(
                        predictionId == null ? "PENDING" : "READY",
                        predictionId,
                        nullableDouble(
                                resultSet,
                                "approval_probability"
                        ),
                        nullableDouble(
                                resultSet,
                                "popularity_score"
                        ),
                        nullableDouble(
                                resultSet,
                                "vote_adjustment"
                        ),
                        nullableDouble(
                                resultSet,
                                "final_score"
                        ),
                        resultSet.getString("model_version"),
                        toLocalDateTime(
                                resultSet.getTimestamp("predicted_at")
                        )
                );

        boolean admin = ADMIN_ROLES.contains(requester.role());

        /*
         * 현재 시민투표 목록의 실투표 대상은 PENDING입니다.
         * REVIEWING은 상태 표시에는 대응하지만 투표 버튼은 열지 않습니다.
         */
        boolean canVote = !admin
                && !owner
                && "PENDING".equals(status);

        boolean canCancel = owner
                && "PENDING".equals(status);

        String applicantName = resultSet.getString(
                "applicant_display_name"
        );

        if (!admin && !owner) {
            applicantName = maskName(applicantName);
        }

        return new CitizenVoteDetailDtos.DetailResponse(
                resultSet.getLong("application_id"),
                applicantUserId,
                applicantName,
                owner,
                requester.role(),

                resultSet.getString("title"),
                resultSet.getString("author"),
                resultSet.getString("publisher"),
                resultSet.getString("isbn"),
                resultSet.getString("thumbnail_url"),
                toLocalDate(
                        resultSet.getDate("published_date")
                ),

                nullableLong(resultSet, "library_id"),
                resultSet.getString("library_name"),

                resultSet.getString("reason"),
                status,
                resultSet.getString("admin_comment"),
                createdAt,
                toLocalDateTime(
                        resultSet.getTimestamp("processed_at")
                ),

                voteCount,
                recentVoteCount7d,
                resultSet.getBoolean("voted_by_me"),
                canVote,
                canCancel,
                calculatePopularityIndex(
                        voteCount,
                        recentVoteCount7d,
                        createdAt
                ),

                prediction
        );
    }

    private Requester resolveRequester(Long requesterUserId) {
        if (requesterUserId == null || requesterUserId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT
                        user_account.user_id,

                        UPPER(
                            REPLACE(
                                CAST(
                                    user_account.role
                                    AS CHAR
                                ),
                                'ROLE_',
                                ''
                            )
                        ) AS normalized_role,

                        user_account.managed_library_id,

                        COALESCE(
                            NULLIF(
                                TRIM(
                                    user_account.managed_library_code
                                ),
                                ''
                            ),
                            NULLIF(
                                TRIM(
                                    managed_library.lib_code
                                ),
                                ''
                            )
                        ) AS managed_library_code,

                        managed_library.library_name
                            AS managed_library_name

                    FROM users user_account

                    LEFT JOIN libraries managed_library
                      ON managed_library.library_id =
                         user_account.managed_library_id

                    WHERE user_account.user_id = ?
                      AND user_account.status = 'ACTIVE'
                    """,
                    (resultSet, rowNumber) ->
                            new Requester(
                                    resultSet.getLong(
                                            "user_id"
                                    ),
                                    resultSet.getString(
                                            "normalized_role"
                                    ),
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
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "유효한 로그인 사용자를 찾을 수 없습니다."
            );
        }
    }

    private void validateManagedLibrary(
            Requester requester
    ) {
        boolean hasLibraryId =
                requester.managedLibraryId() != null;

        boolean hasLibraryCode =
                StringUtils.hasText(
                        requester.managedLibraryCode()
                );

        boolean hasLibraryName =
                StringUtils.hasText(
                        requester.managedLibraryName()
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
    }

    private void validateApplicationId(Long applicationId) {
        if (applicationId == null || applicationId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "신청 번호가 필요합니다."
            );
        }
    }

    private String normalizeStatus(String value) {
        String normalized = value == null
                ? "PENDING"
                : value.trim().toUpperCase(Locale.ROOT);

        return "CANCELLED".equals(normalized)
                ? "CANCELED"
                : normalized;
    }

    private String maskName(String value) {
        if (value == null || value.isBlank()) {
            return "다른 시민";
        }

        String trimmed = value.trim();

        if (trimmed.length() == 1) {
            return trimmed + "*";
        }

        return trimmed.substring(0, 1)
                + "*".repeat(
                        Math.max(1, trimmed.length() - 1)
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

    private Double nullableDouble(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private LocalDate toLocalDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private record Requester(
            Long userId,
            String role,
            Long managedLibraryId,
            String managedLibraryCode,
            String managedLibraryName
    ) {
    }

    private record ApplicationOwner(
            Long userId,
            String status
    ) {
    }
}
