package com.example.teamproject1.libraryanalytics.service;

import com.example.teamproject1.libraryanalytics.dto.NationalGenreSnapshotRefreshResponse;
import com.example.teamproject1.libraryanalytics.service.Data4LibraryAnalyticsClient.CollectionAggregate;
import com.example.teamproject1.libraryanalytics.service.Data4LibraryAnalyticsClient.LibraryInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NationalGenreSnapshotService {

    private final JdbcTemplate jdbcTemplate;

    private final Data4LibraryAnalyticsClient
            data4LibraryAnalyticsClient;

    @Value("${data4library.analytics.national-refresh-delay-ms:100}")
    private long requestDelayMilliseconds;

    public NationalGenreSnapshotRefreshResponse
    refreshSnapshot(
            Long requesterUserId,
            int maxLibraries
    ) {
        validateMasterAdmin(
                requesterUserId
        );

        List<LibraryInfo> libraries =
                data4LibraryAnalyticsClient
                        .fetchAllLibraries(
                                Math.max(
                                        0,
                                        maxLibraries
                                )
                        );

        Map<String, Long> totals =
                emptyGenreMap();

        int completed = 0;
        int failed = 0;

        for (
                LibraryInfo library :
                libraries
        ) {
            try {
                CollectionAggregate collection =
                        data4LibraryAnalyticsClient
                                .fetchCollection(
                                        library.libraryCode()
                                );

                for (
                        String code :
                        totals.keySet()
                ) {
                    totals.merge(
                            code,
                            collection.genreHoldings()
                                    .getOrDefault(
                                            code,
                                            0L
                                    ),
                            Long::sum
                    );
                }

                completed++;

                log.info(
                        "[NationalGenreSnapshotService] 전국 장서 집계 완료: {}/{} {}",
                        completed + failed,
                        libraries.size(),
                        library.libraryName()
                );
            } catch (Exception exception) {
                failed++;

                log.warn(
                        "[NationalGenreSnapshotService] 도서관 장서 집계 실패: {} ({})",
                        library.libraryName(),
                        library.libraryCode(),
                        exception
                );
            }

            pauseBetweenRequests();
        }

        if (completed == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "전국 장서 스냅샷을 생성할 수 없습니다."
            );
        }

        LocalDate snapshotDate =
                LocalDate.now();

        saveSnapshot(
                snapshotDate,
                totals,
                completed
        );

        long totalHoldingCount =
                totals.values()
                        .stream()
                        .mapToLong(
                                Long::longValue
                        )
                        .sum();

        return new NationalGenreSnapshotRefreshResponse(
                snapshotDate,
                libraries.size(),
                completed,
                failed,
                totalHoldingCount,
                Map.copyOf(
                        totals
                )
        );
    }

    private void saveSnapshot(
            LocalDate snapshotDate,
            Map<String, Long> totals,
            int participatingLibraryCount
    ) {
        jdbcTemplate.update(
                """
                DELETE FROM library_genre_national_snapshot
                WHERE snapshot_date = ?
                """,
                snapshotDate
        );

        for (
                Map.Entry<String, Long>
                        entry :
                totals.entrySet()
        ) {
            jdbcTemplate.update(
                    """
                    INSERT INTO library_genre_national_snapshot (
                        snapshot_date,
                        kdc_code,
                        holding_count,
                        participating_library_count,
                        source_note,
                        created_at
                    )
                    VALUES (
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        CURRENT_TIMESTAMP
                    )
                    """,
                    snapshotDate,
                    entry.getKey(),
                    entry.getValue(),
                    participatingLibraryCount,
                    "도서관정보나루 libSrch + itemSrch API 집계"
            );
        }
    }

    private void validateMasterAdmin(
            Long requesterUserId
    ) {
        if (requesterUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        List<String> roles =
                jdbcTemplate.query(
                        """
                        SELECT role
                        FROM users
                        WHERE user_id = ?
                          AND status = 'ACTIVE'
                        """,
                        (resultSet, rowNumber) ->
                                normalizeRole(
                                        resultSet.getString(
                                                "role"
                                        )
                                ),
                        requesterUserId
                );

        if (
                roles.isEmpty()
                        || !"MASTER_ADMIN".equals(
                        roles.get(0)
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "전국 장서 스냅샷 갱신은 최고 관리자만 실행할 수 있습니다."
            );
        }
    }

    private void pauseBetweenRequests() {
        if (
                requestDelayMilliseconds <= 0
        ) {
            return;
        }

        try {
            Thread.sleep(
                    requestDelayMilliseconds
            );
        } catch (
                InterruptedException exception
        ) {
            Thread.currentThread()
                    .interrupt();

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "전국 장서 집계 작업이 중단되었습니다.",
                    exception
            );
        }
    }

    private Map<String, Long>
    emptyGenreMap() {
        Map<String, Long> result =
                new LinkedHashMap<>();

        for (
                int code = 0;
                code <= 9;
                code++
        ) {
            result.put(
                    String.valueOf(
                            code
                    ),
                    0L
            );
        }

        return result;
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
}
