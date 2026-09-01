package com.example.teamproject1.libraryanalytics.service;

import com.example.teamproject1.libraryanalytics.dto.ManagedLibraryAnalyticsResponse;
import com.example.teamproject1.libraryanalytics.dto.ManagedLibraryAnalyticsResponse.GenreComparison;
import com.example.teamproject1.libraryanalytics.dto.ManagedLibraryAnalyticsResponse.UsagePoint;
import com.example.teamproject1.libraryanalytics.dto.ManagedLibraryAnalyticsResponse.VisitorSummary;
import com.example.teamproject1.libraryanalytics.service.Data4LibraryAnalyticsClient.CollectionAggregate;
import com.example.teamproject1.libraryanalytics.service.Data4LibraryAnalyticsClient.LibraryApiBundle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLibraryAnalyticsService {

    private static final Map<String, String>
    KDC_NAMES =
            createKdcNames();

    private final JdbcTemplate jdbcTemplate;

    private final Data4LibraryAnalyticsClient
            data4LibraryAnalyticsClient;

    @Value("${data4library.analytics.library-cache-hours:6}")
    private long libraryCacheHours;

    @Value("${data4library.analytics.national-cache-hours:24}")
    private long nationalCacheHours;

    private final Map<String, CacheEntry<LibraryApiBundle>>
            libraryCache =
            new ConcurrentHashMap<>();

    private volatile CacheEntry<Map<String, Long>>
            nationalDemandCache;

    public ManagedLibraryAnalyticsResponse
    getManagedLibraryAnalytics(
            Long requesterUserId,
            boolean refresh
    ) {
        AdminScope scope =
                resolveAdminScope(
                        requesterUserId
                );

        if (refresh) {
            libraryCache.remove(
                    scope.libraryCode()
            );

            nationalDemandCache = null;
        }

        LibraryApiBundle bundle =
                loadLibraryBundle(
                        scope.libraryCode()
                );

        NationalMetric nationalMetric =
                loadNationalMetric();

        VisitorSummary visitorSummary =
                loadVisitorSummary(
                        scope.libraryId()
                );

        CollectionAggregate collection =
                bundle.collection();

        long libraryHoldingCount =
                Math.max(
                        collection.holdingCount(),
                        bundle.libraryInfo()
                                .bookCount()
                );

        List<GenreComparison> genreComparison =
                buildGenreComparison(
                        collection.genreHoldings(),
                        nationalMetric.values()
                );

        double loanActivityIndex =
                bundle.dayTrend()
                        .stream()
                        .mapToDouble(
                                UsagePoint::loan
                        )
                        .sum();

        double returnActivityIndex =
                bundle.dayTrend()
                        .stream()
                        .mapToDouble(
                                UsagePoint::returnCount
                        )
                        .sum();

        return new ManagedLibraryAnalyticsResponse(
                scope.libraryId(),
                scope.libraryCode(),
                firstNonBlank(
                        scope.libraryName(),
                        bundle.libraryInfo()
                                .libraryName(),
                        "담당 도서관"
                ),
                libraryHoldingCount,
                collection.cumulativeLoanCount(),
                collection.complete(),
                nationalMetric.mode(),
                nationalMetric.description(),
                loanActivityIndex,
                returnActivityIndex,
                findTopLabel(
                        bundle.dayTrend()
                ),
                findTopLabel(
                        bundle.hourTrend()
                ),
                genreComparison,
                bundle.dayTrend(),
                bundle.hourTrend(),
                visitorSummary,
                LocalDateTime.now()
        );
    }

    private AdminScope resolveAdminScope(
            Long requesterUserId
    ) {
        if (requesterUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        List<AdminScope> results =
                jdbcTemplate.query(
                        """
                        SELECT
                            user.user_id,
                            user.role,
                            user.managed_library_id,

                            COALESCE(
                                NULLIF(
                                    user.managed_library_code,
                                    ''
                                ),
                                library.lib_code
                            ) AS managed_library_code,

                            library.library_name

                        FROM users user

                        LEFT JOIN libraries library
                          ON library.library_id =
                             user.managed_library_id

                        WHERE
                            user.user_id = ?
                            AND user.status = 'ACTIVE'
                        """,
                        (resultSet, rowNumber) ->
                                new AdminScope(
                                        resultSet.getLong(
                                                "user_id"
                                        ),

                                        normalizeRole(
                                                resultSet.getString(
                                                        "role"
                                                )
                                        ),

                                        nullableLong(
                                                resultSet,
                                                "managed_library_id"
                                        ),

                                        resultSet.getString(
                                                "managed_library_code"
                                        ),

                                        resultSet.getString(
                                                "library_name"
                                        )
                                ),
                        requesterUserId
                );

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "활성 관리자 계정을 찾을 수 없습니다."
            );
        }

        AdminScope scope =
                results.get(0);

        if (!"ADMIN".equals(
                scope.role()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "도서관 관리자만 담당 도서관 운영 분석을 조회할 수 있습니다."
            );
        }

        if (
                scope.libraryId() == null
                        || scope.libraryCode() == null
                        || scope.libraryCode()
                        .isBlank()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "담당 도서관 번호와 정보나루 도서관 코드가 필요합니다."
            );
        }

        return scope;
    }

    private LibraryApiBundle loadLibraryBundle(
            String libraryCode
    ) {
        CacheEntry<LibraryApiBundle>
                cached =
                libraryCache.get(
                        libraryCode
                );

        if (
                cached != null
                        && !cached.expired(
                        Duration.ofHours(
                                Math.max(
                                        1L,
                                        libraryCacheHours
                                )
                        )
                )
        ) {
            return cached.value();
        }

        synchronized (
                libraryCache
        ) {
            cached =
                    libraryCache.get(
                            libraryCode
                    );

            if (
                    cached != null
                            && !cached.expired(
                            Duration.ofHours(
                                    Math.max(
                                            1L,
                                            libraryCacheHours
                                    )
                            )
                    )
            ) {
                return cached.value();
            }

            LibraryApiBundle loaded =
                    data4LibraryAnalyticsClient
                            .fetchLibraryBundle(
                                    libraryCode
                            );

            libraryCache.put(
                    libraryCode,
                    new CacheEntry<>(
                            loaded,
                            Instant.now()
                    )
            );

            return loaded;
        }
    }

    private NationalMetric loadNationalMetric() {
        Optional<NationalMetric>
                snapshot =
                loadNationalHoldingsSnapshot();

        if (snapshot.isPresent()) {
            return snapshot.get();
        }

        try {
            Map<String, Long>
                    nationalLoanDemand =
                    loadNationalLoanDemand();

            long total =
                    nationalLoanDemand.values()
                            .stream()
                            .mapToLong(
                                    Long::longValue
                            )
                            .sum();

            if (total > 0L) {
                return new NationalMetric(
                        "NATIONAL_LOAN_DEMAND",
                        nationalLoanDemand,
                        "전국 장서 스냅샷이 없어 최근 1년 전국 인기대출 데이터의 "
                                + "KDC별 대출 비중과 담당 도서관 장서 비중을 비교합니다."
                );
            }
        } catch (Exception exception) {
            log.warn(
                    "[AdminLibraryAnalyticsService] 전국 장르 기준 조회 실패. "
                            + "담당 도서관 데이터만 표시합니다.",
                    exception
            );
        }

        return new NationalMetric(
                "NATIONAL_DATA_UNAVAILABLE",
                emptyKdcMap(),
                "전국 장르 기준 데이터를 일시적으로 불러오지 못했습니다. "
                        + "담당 도서관의 장르별 장서 현황은 정상적으로 표시합니다."
        );
    }

    private Optional<NationalMetric>
    loadNationalHoldingsSnapshot() {
        try {
            List<SnapshotRow> rows =
                    jdbcTemplate.query(
                            """
                            SELECT
                                snapshot.kdc_code,
                                snapshot.holding_count,
                                snapshot.snapshot_date

                            FROM library_genre_national_snapshot snapshot

                            WHERE snapshot.snapshot_date = (
                                SELECT MAX(
                                    latest.snapshot_date
                                )
                                FROM library_genre_national_snapshot latest
                            )

                            ORDER BY snapshot.kdc_code
                            """,
                            (resultSet, rowNumber) ->
                                    new SnapshotRow(
                                            resultSet.getString(
                                                    "kdc_code"
                                            ),

                                            resultSet.getLong(
                                                    "holding_count"
                                            ),

                                            resultSet.getDate(
                                                    "snapshot_date"
                                            )
                                                    .toLocalDate()
                                    )
                    );

            if (rows.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Long> values =
                    emptyKdcMap();

            for (
                    SnapshotRow row :
                    rows
            ) {
                if (
                        KDC_NAMES.containsKey(
                                row.kdcCode()
                        )
                ) {
                    values.put(
                            row.kdcCode(),
                            Math.max(
                                    0L,
                                    row.holdingCount()
                            )
                    );
                }
            }

            long total =
                    values.values()
                            .stream()
                            .mapToLong(
                                    Long::longValue
                            )
                            .sum();

            if (total <= 0L) {
                return Optional.empty();
            }

            LocalDate snapshotDate =
                    rows.get(0)
                            .snapshotDate();

            return Optional.of(
                    new NationalMetric(
                            "NATIONAL_HOLDINGS",
                            values,
                            "전국 참여 도서관 장서 스냅샷 "
                                    + snapshotDate
                                    + " 기준 KDC별 장서 비중과 담당 도서관을 비교합니다."
                    )
            );
        } catch (
                DataAccessException exception
        ) {
            log.debug(
                    "[AdminLibraryAnalyticsService] 전국 장서 스냅샷 조회 불가: {}",
                    exception.getMessage()
            );

            return Optional.empty();
        }
    }

    private Map<String, Long>
    loadNationalLoanDemand() {
        CacheEntry<Map<String, Long>>
                cached =
                nationalDemandCache;

        Duration ttl =
                Duration.ofHours(
                        Math.max(
                                1L,
                                nationalCacheHours
                        )
                );

        if (
                cached != null
                        && !cached.expired(
                        ttl
                )
        ) {
            return cached.value();
        }

        synchronized (
                this
        ) {
            cached =
                    nationalDemandCache;

            if (
                    cached != null
                            && !cached.expired(
                            ttl
                    )
            ) {
                return cached.value();
            }

            Map<String, Long> loaded =
                    data4LibraryAnalyticsClient
                            .fetchNationalLoanDemand();

            nationalDemandCache =
                    new CacheEntry<>(
                            loaded,
                            Instant.now()
                    );

            return loaded;
        }
    }

    private VisitorSummary loadVisitorSummary(
            Long libraryId
    ) {
        try {
            List<VisitorSummary> results =
                    jdbcTemplate.query(
                            """
                            SELECT
                                COALESCE(
                                    SUM(
                                        CASE
                                            WHEN stat.stat_date =
                                                 CURRENT_DATE
                                            THEN stat.visitor_count
                                            ELSE 0
                                        END
                                    ),
                                    0
                                ) AS today_visitors,

                                COALESCE(
                                    SUM(
                                        stat.visitor_count
                                    ),
                                    0
                                ) AS visitors_30_days,

                                COALESCE(
                                    SUM(
                                        stat.active_borrower_count
                                    ),
                                    0
                                ) AS active_borrowers_30_days,

                                COALESCE(
                                    SUM(
                                        stat.program_participant_count
                                    ),
                                    0
                                ) AS program_participants_30_days,

                                MIN(
                                    stat.stat_date
                                ) AS data_start_date,

                                MAX(
                                    stat.stat_date
                                ) AS data_end_date

                            FROM library_daily_statistics stat

                            WHERE
                                stat.library_id = ?
                                AND stat.stat_date >=
                                    DATE_SUB(
                                        CURRENT_DATE,
                                        INTERVAL 29 DAY
                                    )
                            """,
                            (resultSet, rowNumber) -> {
                                java.sql.Date startSqlDate =
                                        resultSet.getDate(
                                                "data_start_date"
                                        );

                                java.sql.Date endSqlDate =
                                        resultSet.getDate(
                                                "data_end_date"
                                        );

                                LocalDate startDate =
                                        startSqlDate == null
                                                ? null
                                                : startSqlDate.toLocalDate();

                                LocalDate endDate =
                                        endSqlDate == null
                                                ? null
                                                : endSqlDate.toLocalDate();

                                return new VisitorSummary(
                                        startDate != null,
                                        resultSet.getLong(
                                                "today_visitors"
                                        ),
                                        resultSet.getLong(
                                                "visitors_30_days"
                                        ),
                                        resultSet.getLong(
                                                "active_borrowers_30_days"
                                        ),
                                        resultSet.getLong(
                                                "program_participants_30_days"
                                        ),
                                        startDate,
                                        endDate
                                );
                            },
                            libraryId
                    );

            return results.isEmpty()
                    ? VisitorSummary.unavailable()
                    : results.get(0);
        } catch (
                DataAccessException exception
        ) {
            log.debug(
                    "[AdminLibraryAnalyticsService] 이용객 통계 테이블 조회 불가: {}",
                    exception.getMessage()
            );

            return VisitorSummary.unavailable();
        }
    }

    private List<GenreComparison>
    buildGenreComparison(
            Map<String, Long> libraryValues,
            Map<String, Long> nationalValues
    ) {
        long libraryTotal =
                KDC_NAMES.keySet()
                        .stream()
                        .mapToLong(
                                code ->
                                        Math.max(
                                                0L,
                                                libraryValues.getOrDefault(
                                                        code,
                                                        0L
                                                )
                                        )
                        )
                        .sum();

        long nationalTotal =
                KDC_NAMES.keySet()
                        .stream()
                        .mapToLong(
                                code ->
                                        Math.max(
                                                0L,
                                                nationalValues.getOrDefault(
                                                        code,
                                                        0L
                                                )
                                        )
                        )
                        .sum();

        List<GenreComparison> result =
                new ArrayList<>();

        for (
                Map.Entry<String, String>
                        entry :
                KDC_NAMES.entrySet()
        ) {
            String code =
                    entry.getKey();

            long libraryCount =
                    Math.max(
                            0L,
                            libraryValues.getOrDefault(
                                    code,
                                    0L
                            )
                    );

            long nationalValue =
                    Math.max(
                            0L,
                            nationalValues.getOrDefault(
                                    code,
                                    0L
                            )
                    );

            double libraryShare =
                    libraryTotal <= 0
                            ? 0D
                            : libraryCount
                            * 100D
                            / libraryTotal;

            double nationalShare =
                    nationalTotal <= 0
                            ? 0D
                            : nationalValue
                            * 100D
                            / nationalTotal;

            result.add(
                    new GenreComparison(
                            code,
                            entry.getValue(),
                            nationalValue,
                            libraryCount,
                            roundOne(
                                    nationalShare
                            ),
                            roundOne(
                                    libraryShare
                            ),
                            roundOne(
                                    libraryShare
                                            - nationalShare
                            )
                    )
            );
        }

        return result;
    }

    private String findTopLabel(
            List<UsagePoint> points
    ) {
        return points.stream()
                .max(
                        (
                                first,
                                second
                        ) ->
                                Double.compare(
                                        first.loan(),
                                        second.loan()
                                )
                )
                .map(
                        UsagePoint::label
                )
                .orElse("-");
    }

    private Long nullableLong(
            java.sql.ResultSet resultSet,
            String columnName
    ) throws java.sql.SQLException {
        long value =
                resultSet.getLong(
                        columnName
                );

        return resultSet.wasNull()
                ? null
                : value;
    }

    private String normalizeRole(
            String value
    ) {
        if (value == null) {
            return "USER";
        }

        return value.trim()
                .toUpperCase()
                .replaceFirst(
                        "^ROLE_",
                        ""
                );
    }

    private String firstNonBlank(
            String... values
    ) {
        for (
                String value :
                values
        ) {
            if (
                    value != null
                            && !value.isBlank()
            ) {
                return value;
            }
        }

        return "";
    }

    private double roundOne(
            double value
    ) {
        return Math.round(
                value * 10D
        ) / 10D;
    }

    private Map<String, Long>
    emptyKdcMap() {
        Map<String, Long> result =
                new LinkedHashMap<>();

        for (
                String code :
                KDC_NAMES.keySet()
        ) {
            result.put(
                    code,
                    0L
            );
        }

        return result;
    }

    private static Map<String, String>
    createKdcNames() {
        Map<String, String> result =
                new LinkedHashMap<>();

        result.put("0", "총류");
        result.put("1", "철학");
        result.put("2", "종교");
        result.put("3", "사회과학");
        result.put("4", "자연과학");
        result.put("5", "기술과학");
        result.put("6", "예술");
        result.put("7", "언어");
        result.put("8", "문학");
        result.put("9", "역사");

        return Map.copyOf(
                result
        );
    }

    private record AdminScope(
            Long userId,
            String role,
            Long libraryId,
            String libraryCode,
            String libraryName
    ) {
    }

    private record NationalMetric(
            String mode,
            Map<String, Long> values,
            String description
    ) {
    }

    private record SnapshotRow(
            String kdcCode,
            long holdingCount,
            LocalDate snapshotDate
    ) {
    }

    private record CacheEntry<T>(
            T value,
            Instant loadedAt
    ) {
        private boolean expired(
                Duration ttl
        ) {
            return loadedAt
                    .plus(ttl)
                    .isBefore(
                            Instant.now()
                    );
        }
    }
}
