package com.example.teamproject1.vote.detail.service;

import com.example.teamproject1.book.classification.BookClassificationService;
import com.example.teamproject1.book.dto.BookClassificationResponse;
import com.example.teamproject1.vote.detail.dto.CitizenVoteAiDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class CitizenVoteAiPredictionService {

    private static final String CURRENT_MODEL_VERSION =
            "library-ai-v4.1-dynamic-library-20260722-v3";

    private static final Duration CACHE_DURATION =
            Duration.ofMinutes(30);

    private static final Map<String, Double> CATEGORY_KDC_FALLBACK =
            Map.ofEntries(
                    Map.entry("총류", 0.0),
                    Map.entry("철학", 100.0),
                    Map.entry("종교", 200.0),
                    Map.entry("사회과학", 300.0),
                    Map.entry("자연과학", 400.0),
                    Map.entry("기술과학", 500.0),
                    Map.entry("예술", 600.0),
                    Map.entry("언어", 700.0),
                    Map.entry("문학", 800.0),
                    Map.entry("역사", 900.0)
            );

    private final JdbcTemplate jdbcTemplate;
    private final RestClient aiRestClient;
    private final BookClassificationService bookClassificationService;

    private final ConcurrentHashMap<Long, ReentrantLock>
            applicationLocks =
            new ConcurrentHashMap<>();

    public CitizenVoteAiPredictionService(
            JdbcTemplate jdbcTemplate,
            @Qualifier("bookcastAiRestClient")
            RestClient aiRestClient,
            BookClassificationService bookClassificationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiRestClient = aiRestClient;
        this.bookClassificationService = bookClassificationService;
    }

    public CitizenVoteAiDtos.SavedPrediction predictAndSave(
            Long requesterUserId,
            Long applicationId,
            boolean force
    ) {
        validateId(requesterUserId, "회원 번호");
        validateId(applicationId, "신청 번호");

        ReentrantLock lock = applicationLocks.computeIfAbsent(
                applicationId,
                ignored -> new ReentrantLock()
        );

        lock.lock();

        try {
            PredictionInput input = loadPredictionInput(
                    requesterUserId,
                    applicationId
            );

            CitizenVoteAiDtos.SavedPrediction cached =
                    findReusablePrediction(
                            applicationId,
                            force
                    );

            if (cached != null) {
                return cached;
            }

            double kdc = resolveKdc(
                    input.isbn(),
                    input.categoryName()
            );

            CitizenVoteAiDtos.PredictionRequest request =
                    new CitizenVoteAiDtos.PredictionRequest(
                            input.applicationId(),
                            input.bookId(),
                            input.title(),
                            input.author(),
                            input.publisher(),
                            kdc,
                            input.libraryName(),
                            input.voteCount(),
                            input.recentVoteCount7d()
                    );

            CitizenVoteAiDtos.PredictionResponse response;

            try {
                response = aiRestClient
                        .post()
                        .uri("/api/ai/predict-approval")
                        .body(request)
                        .retrieve()
                        .body(
                                CitizenVoteAiDtos
                                        .PredictionResponse
                                        .class
                        );
            } catch (RestClientException exception) {
                log.error(
                        "[CitizenVoteAiPredictionService] AI 서버 호출 실패. applicationId={}",
                        applicationId,
                        exception
                );

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI 예상 승인율 서버에 연결하지 못했습니다. "
                                + "FastAPI 서버가 8000번 포트에서 실행 중인지 확인해주세요.",
                        exception
                );
            }

            validateAiResponse(response);

            long predictionId = insertPrediction(
                    input,
                    response
            );

            return new CitizenVoteAiDtos.SavedPrediction(
                    predictionId,
                    input.applicationId(),
                    input.bookId(),
                    response.approvalProbability(),
                    response.popularityScore(),
                    response.voteAdjustment(),
                    response.finalScore(),
                    response.modelVersion()
            );
        } finally {
            lock.unlock();

            if (!lock.hasQueuedThreads()) {
                applicationLocks.remove(
                        applicationId,
                        lock
                );
            }
        }
    }

    private PredictionInput loadPredictionInput(
            Long requesterUserId,
            Long applicationId
    ) {
        Requester requester = resolveRequester(
                requesterUserId
        );

        String scopeSql = "";

        List<Object> parameters =
                new ArrayList<>();

        parameters.add(applicationId);

        if ("ADMIN".equals(requester.role())) {
            validateManagedLibrary(requester);

            scopeSql =
                    """
                      AND (
                            application.library_id = ?
                            OR (
                                ? IS NOT NULL
                                AND UPPER(
                                    TRIM(
                                        COALESCE(
                                            application_library.lib_code,
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
                                                application_library.library_name,
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
                    """;

            parameters.add(
                    requester.managedLibraryId()
            );

            parameters.add(
                    requester.managedLibraryCode()
            );

            parameters.add(
                    requester.managedLibraryCode()
            );

            parameters.add(
                    requester.managedLibraryName()
            );

            parameters.add(
                    requester.managedLibraryName()
            );
        }

        String sql =
                """
                SELECT
                    application.application_id,

                    COALESCE(
                        application.book_id,
                        book_by_isbn.book_id
                    ) AS resolved_book_id,

                    application.title,
                    application.author,

                    COALESCE(
                        NULLIF(TRIM(book_by_id.publisher), ''),
                        NULLIF(TRIM(book_by_isbn.publisher), ''),
                        '미상'
                    ) AS publisher,

                    application.isbn,

                    COALESCE(
                        NULLIF(
                            TRIM(
                                application_library.library_name
                            ),
                            ''
                        ),
                        '화정도서관'
                    ) AS library_name,

                    COALESCE(
                        application_category.category_name,
                        book_category.category_name
                    ) AS category_name,

                    COALESCE(
                        vote_summary.vote_count,
                        0
                    ) AS vote_count,

                    COALESCE(
                        vote_summary.recent_vote_count_7d,
                        0
                    ) AS recent_vote_count_7d

                FROM hope_applications application

                LEFT JOIN books book_by_id
                  ON book_by_id.book_id =
                     application.book_id

                LEFT JOIN books book_by_isbn
                  ON book_by_isbn.book_id = (
                      SELECT candidate.book_id
                      FROM books candidate
                      WHERE UPPER(
                                REPLACE(
                                    REPLACE(candidate.isbn, '-', ''),
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
                              WHEN candidate.book_id =
                                   application.book_id
                              THEN 0
                              ELSE 1
                          END,
                          candidate.updated_at DESC,
                          candidate.book_id DESC
                      LIMIT 1
                  )

                LEFT JOIN libraries application_library
                  ON application_library.library_id =
                     application.library_id

                LEFT JOIN categories application_category
                  ON application_category.category_id =
                     application.category_id

                LEFT JOIN categories book_category
                  ON book_category.category_id =
                     COALESCE(
                         book_by_id.category_id,
                         book_by_isbn.category_id
                     )

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

                WHERE application.application_id = ?
                  AND COALESCE(
                        application.is_hidden,
                        FALSE
                      ) = FALSE
                """
                        + scopeSql;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    (resultSet, rowNumber) ->
                            mapPredictionInput(
                                    resultSet
                            ),
                    parameters.toArray()
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 권한이 없거나 희망도서 신청을 찾을 수 없습니다."
            );
        } catch (DataAccessException exception) {
            log.error(
                    "[CitizenVoteAiPredictionService] 예측 입력 조회 실패. applicationId={}",
                    applicationId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 예측 입력정보를 조회하지 못했습니다.",
                    exception
            );
        }
    }

    private PredictionInput mapPredictionInput(
            ResultSet resultSet
    ) throws SQLException {
        return new PredictionInput(
                resultSet.getLong("application_id"),
                nullableLong(
                        resultSet,
                        "resolved_book_id"
                ),
                safeText(
                        resultSet.getString("title"),
                        "도서 제목 없음"
                ),
                safeText(
                        resultSet.getString("author"),
                        "미상"
                ),
                safeText(
                        resultSet.getString("publisher"),
                        "미상"
                ),
                resultSet.getString("isbn"),
                resultSet.getString("library_name"),
                resultSet.getString("category_name"),
                resultSet.getLong("vote_count"),
                resultSet.getLong(
                        "recent_vote_count_7d"
                )
        );
    }

    private CitizenVoteAiDtos.SavedPrediction
    findReusablePrediction(
            Long applicationId,
            boolean force
    ) {
        if (force) {
            return null;
        }

        try {
            ExistingPrediction existing =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT
                                prediction_id,
                                application_id,
                                book_id,
                                approval_probability,
                                popularity_score,
                                vote_adjustment,
                                final_score,
                                model_version,
                                created_at
                            FROM ai_predictions
                            WHERE application_id = ?
                            ORDER BY
                                prediction_id DESC
                            LIMIT 1
                            """,
                            (
                                    resultSet,
                                    rowNumber
                            ) ->
                                    mapExistingPrediction(
                                            resultSet
                                    ),
                            applicationId
                    );

            if (existing == null) {
                return null;
            }

            boolean currentModel =
                    CURRENT_MODEL_VERSION.equals(
                            existing.modelVersion()
                    );

            boolean fresh =
                    existing.createdAt() != null
                            && existing.createdAt()
                            .plus(CACHE_DURATION)
                            .isAfter(
                                    LocalDateTime.now()
                            );

            if (!currentModel || !fresh) {
                return null;
            }

            return new CitizenVoteAiDtos.SavedPrediction(
                    existing.predictionId(),
                    existing.applicationId(),
                    existing.bookId(),
                    existing.approvalProbability(),
                    existing.popularityScore(),
                    existing.voteAdjustment(),
                    existing.finalScore(),
                    existing.modelVersion()
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private ExistingPrediction mapExistingPrediction(
            ResultSet resultSet
    ) throws SQLException {
        return new ExistingPrediction(
                resultSet.getLong("prediction_id"),
                resultSet.getLong("application_id"),
                nullableLong(
                        resultSet,
                        "book_id"
                ),
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
                        resultSet.getTimestamp("created_at")
                )
        );
    }

    private double resolveKdc(
            String isbn,
            String categoryName
    ) {
        if (StringUtils.hasText(isbn)) {
            try {
                BookClassificationResponse classification =
                        bookClassificationService
                                .getAndPersistClassification(
                                        isbn
                                );

                Double parsedClassNo = parseKdc(
                        classification.classNo()
                );

                if (parsedClassNo != null) {
                    return parsedClassNo;
                }

                Double mappedCategory =
                        mapCategoryToKdc(
                                classification.categoryName()
                        );

                if (mappedCategory != null) {
                    return mappedCategory;
                }
            } catch (Exception exception) {
                log.warn(
                        "[CitizenVoteAiPredictionService] 정보나루 KDC 조회 실패. isbn={}, message={}",
                        isbn,
                        exception.getMessage()
                );
            }
        }

        Double mappedCategory =
                mapCategoryToKdc(
                        categoryName
                );

        // 도서 분류정보가 전혀 없는 경우 기본 KDC를 문학(800)으로 사용
        return mappedCategory != null
                ? mappedCategory
                : 800.0;
    }

    private Double parseKdc(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value
                .trim()
                .replaceAll("[^0-9.]", "");

        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        try {
            double parsed = Double.parseDouble(
                    normalized
            );

            return parsed >= 0.0
                    ? parsed
                    : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double mapCategoryToKdc(
            String categoryName
    ) {
        if (!StringUtils.hasText(categoryName)) {
            return null;
        }

        String normalized = categoryName
                .trim()
                .replace(" ", "");

        for (
                Map.Entry<String, Double> entry :
                CATEGORY_KDC_FALLBACK.entrySet()
        ) {
            if (
                    normalized.contains(
                            entry.getKey()
                                    .replace(" ", "")
                    )
            ) {
                return entry.getValue();
            }
        }

        return null;
    }

    private long insertPrediction(
            PredictionInput input,
            CitizenVoteAiDtos.PredictionResponse response
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_predictions (
                    application_id,
                    book_id,
                    approval_probability,
                    popularity_score,
                    vote_adjustment,
                    final_score,
                    model_version,
                    created_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    CURRENT_TIMESTAMP
                )
                """,
                input.applicationId(),
                input.bookId(),
                requireScore(
                        response.approvalProbability(),
                        "예상 승인율"
                ),
                requireScore(
                        response.popularityScore(),
                        "AI 인기도"
                ),
                requireScore(
                        response.voteAdjustment(),
                        "투표 보정값"
                ),
                requireScore(
                        response.finalScore(),
                        "최종 추천점수"
                ),
                safeText(
                        response.modelVersion(),
                        CURRENT_MODEL_VERSION
                )
        );

        Long predictionId =
                jdbcTemplate.queryForObject(
                        "SELECT LAST_INSERT_ID()",
                        Long.class
                );

        if (predictionId == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 예측 결과 저장 후 번호를 확인하지 못했습니다."
            );
        }

        return predictionId;
    }

    private Requester resolveRequester(
            Long requesterUserId
    ) {
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
                    (
                            resultSet,
                            rowNumber
                    ) ->
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

    private void validateAiResponse(
            CitizenVoteAiDtos.PredictionResponse response
    ) {
        if (response == null || !response.success()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 서버가 정상적인 예측 결과를 반환하지 않았습니다."
            );
        }

        requireScore(
                response.approvalProbability(),
                "예상 승인율"
        );

        requireScore(
                response.popularityScore(),
                "AI 인기도"
        );

        requireScore(
                response.voteAdjustment(),
                "투표 보정값"
        );

        requireScore(
                response.finalScore(),
                "최종 추천점수"
        );
    }

    private double requireScore(
            Double value,
            String label
    ) {
        if (
                value == null
                        || !Double.isFinite(value)
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 서버의 " + label
                            + " 값이 올바르지 않습니다."
            );
        }

        return Math.max(
                0.0,
                Math.min(
                        100.0,
                        value
                )
        );
    }

    private void validateId(
            Long value,
            String label
    ) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    label + "가 필요합니다."
            );
        }
    }

    private String safeText(
            String value,
            String fallback
    ) {
        return StringUtils.hasText(value)
                ? value.trim()
                : fallback;
    }

    private Long nullableLong(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        long value = resultSet.getLong(column);

        return resultSet.wasNull()
                ? null
                : value;
    }

    private Double nullableDouble(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        double value = resultSet.getDouble(column);

        return resultSet.wasNull()
                ? null
                : value;
    }

    private LocalDateTime toLocalDateTime(
            Timestamp timestamp
    ) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }

    private record Requester(
            Long userId,
            String role,
            Long managedLibraryId,
            String managedLibraryCode,
            String managedLibraryName
    ) {
    }

    private record PredictionInput(
            Long applicationId,
            Long bookId,
            String title,
            String author,
            String publisher,
            String isbn,
            String libraryName,
            String categoryName,
            long voteCount,
            long recentVoteCount7d
    ) {
    }

    private record ExistingPrediction(
            Long predictionId,
            Long applicationId,
            Long bookId,
            Double approvalProbability,
            Double popularityScore,
            Double voteAdjustment,
            Double finalScore,
            String modelVersion,
            LocalDateTime createdAt
    ) {
    }
}
