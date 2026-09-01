package com.example.teamproject1.main.airecommendation.service;

import com.example.teamproject1.book.classification.BookClassificationService;
import com.example.teamproject1.book.dto.BookClassificationResponse;
import com.example.teamproject1.main.airecommendation.dto.MainAiRecommendationDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class MainAiRecommendationService {

    private static final String MODEL_VERSION =
            "library-ai-v4.1-dynamic-library-20260722-v3";

    private static final int MAX_CANDIDATE_COUNT = 20;

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

    private final RestClient aiRestClient;
    private final BookClassificationService
            bookClassificationService;

    private final ConcurrentHashMap<String, CacheEntry>
            cache = new ConcurrentHashMap<>();

    public MainAiRecommendationService(
            @Qualifier("bookcastAiRestClient")
            RestClient aiRestClient,
            BookClassificationService
                    bookClassificationService
    ) {
        this.aiRestClient = aiRestClient;
        this.bookClassificationService =
                bookClassificationService;
    }

    public List<
            MainAiRecommendationDtos.RecommendedBook
            >
    recommend(
            MainAiRecommendationDtos.Request request
    ) {
        if (
                request == null
                        || request.candidates() == null
                        || request.candidates().isEmpty()
        ) {
            return List.of();
        }

        int limit = normalizeLimit(
                request.limit()
        );

        List<MainAiRecommendationDtos.Candidate>
                candidates =
                request.candidates()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(
                                candidate ->
                                        StringUtils.hasText(
                                                normalizeIsbn(
                                                        candidate.isbn13()
                                                )
                                        )
                        )
                        .limit(MAX_CANDIDATE_COUNT)
                        .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        boolean force =
                Boolean.TRUE.equals(
                        request.force()
                );

        String cacheKey =
                createCacheKey(
                        candidates,
                        limit
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
                return cachedEntry.books()
                        .stream()
                        .map(
                                book ->
                                        withCacheFlag(
                                                book,
                                                true
                                        )
                        )
                        .toList();
            }
        }

        List<MainAiRecommendationDtos.RecommendedBook>
                sortedAnalyzedBooks =
                candidates
                        .parallelStream()
                        .map(this::analyzeCandidateSafely)
                        .filter(Objects::nonNull)
                        .sorted(
                                Comparator
                                        .comparingDouble(
                                                MainAiRecommendationDtos
                                                        .RecommendedBook
                                                        ::recommendationScore
                                        )
                                        .reversed()
                                        .thenComparing(
                                                Comparator
                                                        .comparingDouble(
                                                                MainAiRecommendationDtos
                                                                        .RecommendedBook
                                                                        ::popularityScore
                                                        )
                                                        .reversed()
                                        )
                                        .thenComparing(
                                                Comparator
                                                        .comparingLong(
                                                                MainAiRecommendationDtos
                                                                        .RecommendedBook
                                                                        ::loanCount
                                                        )
                                                        .reversed()
                                        )
                        )
                        .toList();

        List<MainAiRecommendationDtos.RecommendedBook>
                analyzedBooks =
                selectDistinctScoreBooks(
                        sortedAnalyzedBooks,
                        limit
                );

        if (analyzedBooks.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 추천 결과를 생성하지 못했습니다. "
                            + "FastAPI 서버와 모델 파일을 확인해주세요."
            );
        }

        AtomicInteger aiRank =
                new AtomicInteger(1);

        List<MainAiRecommendationDtos.RecommendedBook>
                rankedBooks =
                analyzedBooks
                        .stream()
                        .map(
                                book ->
                                        withRank(
                                                book,
                                                aiRank.getAndIncrement()
                                        )
                        )
                        .toList();

        cache.put(
                cacheKey,
                new CacheEntry(
                        rankedBooks,
                        LocalDateTime.now()
                                .plus(CACHE_DURATION)
                )
        );

        return rankedBooks;
    }

    private MainAiRecommendationDtos.RecommendedBook
    analyzeCandidateSafely(
            MainAiRecommendationDtos.Candidate candidate
    ) {
        try {
            return analyzeCandidate(candidate);
        } catch (Exception exception) {
            log.warn(
                    "[MainAiRecommendationService] 개별 AI 분석 실패. isbn={}, title={}, message={}",
                    candidate.isbn13(),
                    candidate.title(),
                    exception.getMessage()
            );

            return null;
        }
    }

    private MainAiRecommendationDtos.RecommendedBook
    analyzeCandidate(
            MainAiRecommendationDtos.Candidate candidate
    ) {
        String normalizedIsbn =
                normalizeIsbn(
                        candidate.isbn13()
                );

        String title = safeText(
                candidate.title(),
                "도서 제목 없음"
        );

        String author = safeText(
                candidate.author(),
                "미상"
        );

        String publisher = safeText(
                candidate.publisher(),
                "미상"
        );

        double kdc = resolveKdc(
                normalizedIsbn,
                candidate.classNo(),
                candidate.categoryName()
        );

        MainAiRecommendationDtos.AiRequest
                aiRequest =
                new MainAiRecommendationDtos.AiRequest(
                        title,
                        author,
                        publisher,
                        kdc,
                        "전국 기준"
                );

        MainAiRecommendationDtos.AiResponse
                aiResponse;

        try {
            aiResponse = aiRestClient
                    .post()
                    .uri("/api/ai/analyze-priority")
                    .body(aiRequest)
                    .retrieve()
                    .body(
                            MainAiRecommendationDtos
                                    .AiResponse
                                    .class
                    );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 서버 호출 실패",
                    exception
            );
        }

        validateAiResponse(aiResponse);

        double basePriorityScore =
                clampScore(
                        aiResponse.basePriorityScore()
                );

        double popularityScore =
                clampScore(
                        aiResponse.p3AiCapacity()
                );

        double recommendationScore =
                calculateDiversifiedRecommendationScore(
                        basePriorityScore,
                        popularityScore,
                        candidate.rank()
                );

        return new MainAiRecommendationDtos.RecommendedBook(
                0,
                normalizedIsbn,
                title,
                author,
                publisher,
                candidate.imageUrl(),
                safeLong(
                        candidate.loanCount()
                ),
                safeInteger(
                        candidate.rank()
                ),
                candidate.dataStartDate(),
                candidate.dataEndDate(),
                recommendationScore,
                popularityScore,
                nullableClampedScore(
                        aiResponse.p1GenreBalance()
                ),
                nullableClampedScore(
                        aiResponse.p2LocalAffinity()
                ),
                resolveRecommendationLevel(
                        recommendationScore
                ),
                formatKdc(kdc),
                aiResponse.kdcMain(),
                MODEL_VERSION,
                aiResponse.aiComment(),
                false
        );
    }

    private double resolveKdc(
            String isbn13,
            String classNo,
            String categoryName
    ) {
        Double requestKdc =
                parseKdc(classNo);

        if (requestKdc != null) {
            return requestKdc;
        }

        try {
            BookClassificationResponse
                    classification =
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

            Double classificationCategoryKdc =
                    mapCategoryToKdc(
                            classification.categoryName()
                    );

            if (
                    classificationCategoryKdc
                            != null
            ) {
                return classificationCategoryKdc;
            }
        } catch (Exception exception) {
            log.debug(
                    "[MainAiRecommendationService] 정보나루 KDC 조회 실패. isbn={}, message={}",
                    isbn13,
                    exception.getMessage()
            );
        }

        Double categoryKdc =
                mapCategoryToKdc(
                        categoryName
                );

        return categoryKdc != null
                ? categoryKdc
                : 800.0;
    }

    private Double parseKdc(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value
                .trim()
                .replaceAll(
                        "[^0-9.]",
                        ""
                );

        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        try {
            double parsed =
                    Double.parseDouble(
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

        String normalized =
                categoryName
                        .trim()
                        .replace(" ", "");

        for (
                Map.Entry<String, Double> entry :
                CATEGORY_KDC_FALLBACK.entrySet()
        ) {
            if (
                    normalized.contains(
                            entry.getKey()
                                    .replace(
                                            " ",
                                            ""
                                    )
                    )
            ) {
                return entry.getValue();
            }
        }

        return null;
    }

    private void validateAiResponse(
            MainAiRecommendationDtos.AiResponse response
    ) {
        if (
                response == null
                        || !response.success()
                        || response.basePriorityScore()
                        == null
                        || response.p3AiCapacity()
                        == null
                        || !Double.isFinite(
                                response.basePriorityScore()
                        )
                        || !Double.isFinite(
                                response.p3AiCapacity()
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 서버가 올바른 추천 결과를 반환하지 않았습니다."
            );
        }
    }

    private String resolveRecommendationLevel(
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

    private MainAiRecommendationDtos.RecommendedBook
    withRank(
            MainAiRecommendationDtos.RecommendedBook book,
            int aiRank
    ) {
        return new MainAiRecommendationDtos.RecommendedBook(
                aiRank,
                book.isbn13(),
                book.title(),
                book.author(),
                book.publisher(),
                book.imageUrl(),
                book.loanCount(),
                book.rank(),
                book.dataStartDate(),
                book.dataEndDate(),
                book.recommendationScore(),
                book.popularityScore(),
                book.genreBalanceScore(),
                book.localAffinityScore(),
                book.recommendationLevel(),
                book.resolvedKdc(),
                book.kdcMain(),
                book.modelVersion(),
                book.aiComment(),
                book.cached()
        );
    }

    private MainAiRecommendationDtos.RecommendedBook
    withCacheFlag(
            MainAiRecommendationDtos.RecommendedBook book,
            boolean cached
    ) {
        return new MainAiRecommendationDtos.RecommendedBook(
                book.aiRank(),
                book.isbn13(),
                book.title(),
                book.author(),
                book.publisher(),
                book.imageUrl(),
                book.loanCount(),
                book.rank(),
                book.dataStartDate(),
                book.dataEndDate(),
                book.recommendationScore(),
                book.popularityScore(),
                book.genreBalanceScore(),
                book.localAffinityScore(),
                book.recommendationLevel(),
                book.resolvedKdc(),
                book.kdcMain(),
                book.modelVersion(),
                book.aiComment(),
                cached
        );
    }

    private List<
            MainAiRecommendationDtos.RecommendedBook
            >
    selectDistinctScoreBooks(
            List<MainAiRecommendationDtos.RecommendedBook>
                    sortedBooks,
            int limit
    ) {
        java.util.LinkedHashMap<
                String,
                MainAiRecommendationDtos.RecommendedBook
                >
                uniqueByDisplayedScore =
                new java.util.LinkedHashMap<>();

        for (
                MainAiRecommendationDtos.RecommendedBook
                        book :
                sortedBooks
        ) {
            String displayedScoreKey =
                    String.format(
                            Locale.ROOT,
                            "%.1f",
                            book.recommendationScore()
                    );

            uniqueByDisplayedScore
                    .putIfAbsent(
                            displayedScoreKey,
                            book
                    );

            if (
                    uniqueByDisplayedScore.size()
                            >= limit
            ) {
                break;
            }
        }

        return uniqueByDisplayedScore
                .values()
                .stream()
                .limit(limit)
                .toList();
    }

    private double
    calculateDiversifiedRecommendationScore(
            double basePriorityScore,
            double popularityScore,
            Integer popularRank
    ) {
        int normalizedRank =
                popularRank == null
                        || popularRank <= 0
                        ? MAX_CANDIDATE_COUNT
                        : Math.min(
                                MAX_CANDIDATE_COUNT,
                                popularRank
                        );

        double rankScore;

        if (MAX_CANDIDATE_COUNT <= 1) {
            rankScore = 100.0;
        } else {
            rankScore =
                    100.0
                            - (
                            (
                                    normalizedRank
                                            - 1.0
                            )
                                    / (
                                    MAX_CANDIDATE_COUNT
                                            - 1.0
                            )
                    )
                            * 100.0;
        }

        double finalScore =
                basePriorityScore * 0.90
                        + popularityScore * 0.05
                        + rankScore * 0.05;

        return Math.round(
                clampScore(finalScore)
                        * 10.0
        ) / 10.0;
    }

    private int normalizeLimit(Integer value) {
        if (value == null) {
            return 5;
        }

        return Math.max(
                1,
                Math.min(
                        5,
                        value
                )
        );
    }

    private String createCacheKey(
            List<MainAiRecommendationDtos.Candidate>
                    candidates,
            int limit
    ) {
        String signature =
                candidates
                        .stream()
                        .map(
                                candidate ->
                                        String.join(
                                                ":",
                                                normalizeIsbn(
                                                        candidate.isbn13()
                                                ),
                                                safeText(
                                                        candidate.title(),
                                                        ""
                                                ),
                                                safeText(
                                                        candidate.author(),
                                                        ""
                                                ),
                                                safeText(
                                                        candidate.publisher(),
                                                        ""
                                                ),
                                                safeText(
                                                        candidate.classNo(),
                                                        ""
                                                )
                                        )
                        )
                        .reduce(
                                "",
                                (left, right) ->
                                        left + "|" + right
                        );

        return limit
                + ":"
                + Integer.toHexString(
                        signature.hashCode()
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

    private long safeLong(Long value) {
        return value == null
                ? 0L
                : Math.max(
                        0L,
                        value
                );
    }

    private int safeInteger(Integer value) {
        return value == null
                ? 0
                : Math.max(
                        0,
                        value
                );
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

    private record CacheEntry(
            List<MainAiRecommendationDtos.RecommendedBook>
                    books,
            LocalDateTime expiresAt
    ) {
    }
}
