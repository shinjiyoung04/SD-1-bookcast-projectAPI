package com.example.teamproject1.book.aipopularity.service;

import com.example.teamproject1.book.aipopularity.dto.AdminBookAiPopularityDtos;
import com.example.teamproject1.book.classification.BookClassificationService;
import com.example.teamproject1.book.dto.BookClassificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AdminBookAiPopularityService {

    private static final String MODEL_VERSION =
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
    private final BookClassificationService
            bookClassificationService;

    private final ConcurrentHashMap<String, CacheEntry>
            cache = new ConcurrentHashMap<>();

    public AdminBookAiPopularityService(
            JdbcTemplate jdbcTemplate,
            @Qualifier("bookcastAiRestClient")
            RestClient aiRestClient,
            BookClassificationService
                    bookClassificationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiRestClient = aiRestClient;
        this.bookClassificationService =
                bookClassificationService;
    }

    public AdminBookAiPopularityDtos.Response analyze(
            String isbn13,
            AdminBookAiPopularityDtos.Request request
    ) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "AI 인기도 분석 요청정보가 없습니다."
            );
        }

        AdminContext adminContext =
                resolveAdminContext(
                        request.requesterUserId()
                );

        String targetLibraryName =
                "ADMIN".equals(
                        adminContext.role()
                )
                        ? safeText(
                                adminContext.managedLibraryName(),
                                "전국 기준"
                        )
                        : "전국 기준";

        String normalizedIsbn =
                normalizeIsbn(isbn13);

        if (!StringUtils.hasText(normalizedIsbn)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN이 필요합니다."
            );
        }

        String title = safeText(
                request.title(),
                "도서 제목 없음"
        );

        String author = safeText(
                request.author(),
                "미상"
        );

        String publisher = safeText(
                request.publisher(),
                "미상"
        );

        double kdc = resolveKdc(
                normalizedIsbn,
                request.classNo(),
                request.categoryName()
        );

        boolean force =
                Boolean.TRUE.equals(request.force());

        String cacheKey = String.join(
                "|",
                normalizedIsbn,
                title,
                author,
                publisher,
                String.valueOf(kdc),
                targetLibraryName
        );

        if (!force) {
            CacheEntry cachedEntry =
                    cache.get(cacheKey);

            if (
                    cachedEntry != null
                            && cachedEntry.expiresAt()
                            .isAfter(
                                    LocalDateTime.now()
                            )
            ) {
                return withCachedFlag(
                        cachedEntry.response(),
                        true
                );
            }
        }

        AdminBookAiPopularityDtos.AiRequest aiRequest =
                new AdminBookAiPopularityDtos.AiRequest(
                        title,
                        author,
                        publisher,
                        kdc,
                        targetLibraryName
                );

        AdminBookAiPopularityDtos.AiResponse aiResponse;

        try {
            aiResponse = aiRestClient
                    .post()
                    .uri("/api/ai/analyze-priority")
                    .body(aiRequest)
                    .retrieve()
                    .body(
                            AdminBookAiPopularityDtos
                                    .AiResponse
                                    .class
                    );
        } catch (RestClientException exception) {
            log.error(
                    "[AdminBookAiPopularityService] AI 서버 호출 실패. isbn={}",
                    normalizedIsbn,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 인기도 서버에 연결하지 못했습니다. "
                            + "FastAPI 서버가 8000번 포트에서 실행 중인지 확인해주세요.",
                    exception
            );
        }

        validateAiResponse(aiResponse);

        double popularityScore = clampScore(
                aiResponse.p3AiCapacity()
        );

        AdminBookAiPopularityDtos.Response response =
                new AdminBookAiPopularityDtos.Response(
                        normalizedIsbn,
                        "READY",
                        popularityScore,
                        resolvePopularityLevel(
                                popularityScore
                        ),
                        nullableClampedScore(
                                aiResponse.basePriorityScore()
                        ),
                        nullableClampedScore(
                                aiResponse.p1GenreBalance()
                        ),
                        nullableClampedScore(
                                aiResponse.p2LocalAffinity()
                        ),
                        formatKdc(kdc),
                        aiResponse.kdcMain(),
                        safeText(
                                aiResponse.appliedLibrary(),
                                targetLibraryName
                        ),
                        MODEL_VERSION,
                        aiResponse.aiComment(),
                        false
                );

        cache.put(
                cacheKey,
                new CacheEntry(
                        response,
                        LocalDateTime.now()
                                .plus(CACHE_DURATION)
                )
        );

        return response;
    }

    private AdminContext resolveAdminContext(
            Long requesterUserId
    ) {
        if (
                requesterUserId == null
                        || requesterUserId <= 0
        ) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        try {
            AdminContext context =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT
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

                                COALESCE(
                                    NULLIF(
                                        TRIM(
                                            managed_library.library_name
                                        ),
                                        ''
                                    ),
                                    (
                                        SELECT
                                            fallback_library.library_name
                                        FROM libraries fallback_library
                                        WHERE UPPER(
                                                  TRIM(
                                                      fallback_library.lib_code
                                                  )
                                              )
                                              =
                                              UPPER(
                                                  TRIM(
                                                      user_account.managed_library_code
                                                  )
                                              )
                                        ORDER BY
                                            fallback_library.library_id
                                        LIMIT 1
                                    )
                                ) AS managed_library_name

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
                                    new AdminContext(
                                            resultSet.getString(
                                                    "normalized_role"
                                            ),
                                            resultSet.getString(
                                                    "managed_library_name"
                                            )
                                    ),
                            requesterUserId
                    );

            if (
                    context == null
                            || (
                            !"ADMIN".equals(
                                    context.role()
                            )
                                    && !"MASTER_ADMIN".equals(
                                    context.role()
                            )
                    )
            ) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "관리자만 AI 도서 인기도를 조회할 수 있습니다."
                );
            }

            return context;
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "유효한 로그인 사용자를 찾을 수 없습니다."
            );
        }
    }

    private double resolveKdc(
            String isbn13,
            String requestClassNo,
            String requestCategoryName
    ) {
        Double requestKdc =
                parseKdc(requestClassNo);

        if (requestKdc != null) {
            return requestKdc;
        }

        try {
            BookClassificationResponse classification =
                    bookClassificationService
                            .getAndPersistClassification(
                                    isbn13
                            );

            Double classificationKdc =
                    parseKdc(
                            classification.classNo()
                    );

            if (classificationKdc != null) {
                return classificationKdc;
            }

            Double categoryKdc =
                    mapCategoryToKdc(
                            classification.categoryName()
                    );

            if (categoryKdc != null) {
                return categoryKdc;
            }
        } catch (Exception exception) {
            log.warn(
                    "[AdminBookAiPopularityService] 정보나루 KDC 조회 실패. isbn={}, message={}",
                    isbn13,
                    exception.getMessage()
            );
        }

        Double requestCategoryKdc =
                mapCategoryToKdc(
                        requestCategoryName
                );

        return requestCategoryKdc != null
                ? requestCategoryKdc
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
            double parsed =
                    Double.parseDouble(normalized);

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

    private void validateAiResponse(
            AdminBookAiPopularityDtos.AiResponse response
    ) {
        if (
                response == null
                        || !response.success()
                        || response.p3AiCapacity() == null
                        || !Double.isFinite(
                                response.p3AiCapacity()
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 서버가 올바른 인기도 결과를 반환하지 않았습니다."
            );
        }
    }

    private String resolvePopularityLevel(
            double score
    ) {
        if (score >= 80.0) {
            return "VERY_HIGH";
        }

        if (score >= 65.0) {
            return "HIGH";
        }

        if (score >= 45.0) {
            return "MEDIUM";
        }

        if (score >= 25.0) {
            return "LOW";
        }

        return "VERY_LOW";
    }

    private AdminBookAiPopularityDtos.Response
    withCachedFlag(
            AdminBookAiPopularityDtos.Response response,
            boolean cached
    ) {
        return new AdminBookAiPopularityDtos.Response(
                response.isbn13(),
                response.status(),
                response.popularityScore(),
                response.popularityLevel(),
                response.basePriorityScore(),
                response.genreBalanceScore(),
                response.localAffinityScore(),
                response.resolvedKdc(),
                response.kdcMain(),
                response.appliedLibrary(),
                response.modelVersion(),
                response.aiComment(),
                cached
        );
    }

    private double clampScore(Double value) {
        return Math.max(
                0.0,
                Math.min(
                        100.0,
                        value
                )
        );
    }

    private Double nullableClampedScore(
            Double value
    ) {
        if (
                value == null
                        || !Double.isFinite(value)
        ) {
            return null;
        }

        return clampScore(value);
    }

    private String formatKdc(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf(
                    (long) value
            );
        }

        return String.format(
                Locale.ROOT,
                "%.1f",
                value
        );
    }

    private String normalizeIsbn(String value) {
        return String.valueOf(
                        value == null
                                ? ""
                                : value
                )
                .replaceAll(
                        "[^0-9Xx]",
                        ""
                )
                .toUpperCase(
                        Locale.ROOT
                )
                .trim();
    }

    private String safeText(
            String value,
            String fallback
    ) {
        return StringUtils.hasText(value)
                ? value.trim()
                : fallback;
    }

    private record AdminContext(
            String role,
            String managedLibraryName
    ) {
    }

    private record CacheEntry(
            AdminBookAiPopularityDtos.Response response,
            LocalDateTime expiresAt
    ) {
    }
}
