package com.example.teamproject1.libraryanalytics.service;

import com.example.teamproject1.libraryanalytics.dto.ManagedLibraryAnalyticsResponse.UsagePoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class Data4LibraryAnalyticsClient {

    private static final List<String> KDC_CODES =
            List.of(
                    "0",
                    "1",
                    "2",
                    "3",
                    "4",
                    "5",
                    "6",
                    "7",
                    "8",
                    "9"
            );

    @Value("${data4library.base-url:http://data4library.kr/api}")
    private String baseUrl;

    @Value("${data4library.auth-key:}")
    private String authKey;

    @Value("${data4library.analytics.item-page-size:100}")
    private int itemPageSize;

    @Value("${data4library.analytics.max-item-pages:200}")
    private int maxItemPages;

    @Value("${data4library.analytics.national-loan-page-size:200}")
    private int nationalLoanPageSize;

    private final ObjectMapper objectMapper;

    private final RestClient restClient =
            RestClient.create();

    public LibraryApiBundle fetchLibraryBundle(
            String libraryCode
    ) {
        validateConfiguration();

        LibraryInfo libraryInfo =
                safeLibraryInfo(
                        libraryCode
                );

        CollectionAggregate collection =
                safeCollectionAggregate(
                        libraryCode
                );

        List<UsagePoint> dayTrend =
                safeUsageTrend(
                        libraryCode,
                        "D"
                );

        List<UsagePoint> hourTrend =
                safeUsageTrend(
                        libraryCode,
                        "H"
                );

        return new LibraryApiBundle(
                libraryInfo,
                collection,
                dayTrend,
                hourTrend
        );
    }

    private LibraryInfo safeLibraryInfo(
            String libraryCode
    ) {
        try {
            return fetchLibraryInfo(
                    libraryCode
            );
        } catch (Exception exception) {
            log.warn(
                    "[Data4LibraryAnalyticsClient] 도서관 기본정보 조회 실패. "
                            + "장서 데이터만으로 계속 진행합니다. libCode={}",
                    libraryCode,
                    exception
            );

            return new LibraryInfo(
                    libraryCode,
                    null,
                    0L
            );
        }
    }

    private CollectionAggregate safeCollectionAggregate(
            String libraryCode
    ) {
        try {
            return fetchCollectionAggregate(
                    libraryCode
            );
        } catch (Exception exception) {
            log.warn(
                    "[Data4LibraryAnalyticsClient] 도서관 장서 조회 실패. "
                            + "빈 장서 통계로 계속 진행합니다. libCode={}",
                    libraryCode,
                    exception
            );

            return new CollectionAggregate(
                    0L,
                    0L,
                    emptyKdcMap(),
                    false
            );
        }
    }

    private List<UsagePoint> safeUsageTrend(
            String libraryCode,
            String type
    ) {
        try {
            return fetchUsageTrend(
                    libraryCode,
                    type
            );
        } catch (Exception exception) {
            log.warn(
                    "[Data4LibraryAnalyticsClient] 도서관 대출반납 추이 조회 실패. "
                            + "빈 추이 데이터로 계속 진행합니다. libCode={}, type={}",
                    libraryCode,
                    type,
                    exception
            );

            return List.of();
        }
    }

    public CollectionAggregate fetchCollection(
            String libraryCode
    ) {
        validateConfiguration();

        return fetchCollectionAggregate(
                libraryCode
        );
    }

    public List<LibraryInfo> fetchAllLibraries(
            int maxLibraries
    ) {
        validateConfiguration();

        int pageSize = 1000;
        int pageNo = 1;
        long numFound = Long.MAX_VALUE;

        List<LibraryInfo> result =
                new ArrayList<>();

        while (
                result.size() < numFound
        ) {
            URI uri =
                    baseUri("/libSrch")
                            .queryParam(
                                    "authKey",
                                    authKey
                            )
                            .queryParam(
                                    "format",
                                    "json"
                            )
                            .queryParam(
                                    "pageNo",
                                    pageNo
                            )
                            .queryParam(
                                    "pageSize",
                                    pageSize
                            )
                            .build()
                            .encode()
                            .toUri();

            JsonNode response =
                    requestJson(
                            uri,
                            "전국 참여 도서관 "
                                    + pageNo
                                    + "페이지"
                    );

            numFound =
                    firstLong(
                            response,
                            0L,
                            "numFound"
                    );

            List<JsonNode> libraries =
                    extractLibraryNodes(
                            response.path("libs")
                    );

            if (libraries.isEmpty()) {
                break;
            }

            for (
                    JsonNode library :
                    libraries
            ) {
                String libraryCode =
                        firstText(
                                library,
                                null,
                                "libCode",
                                "lib_code"
                        );

                if (
                        libraryCode == null
                                || libraryCode.isBlank()
                ) {
                    continue;
                }

                result.add(
                        new LibraryInfo(
                                libraryCode,
                                firstText(
                                        library,
                                        null,
                                        "libName",
                                        "libraryName",
                                        "name"
                                ),
                                firstLong(
                                        library,
                                        0L,
                                        "BookCount",
                                        "bookCount",
                                        "book_count"
                                )
                        )
                );

                if (
                        maxLibraries > 0
                                && result.size()
                                >= maxLibraries
                ) {
                    return result;
                }
            }

            pageNo++;

            if (
                    pageNo > 100
            ) {
                break;
            }
        }

        return result;
    }

    public Map<String, Long>
    fetchNationalLoanDemand() {
        validateConfiguration();

        LocalDate endDate =
                LocalDate.now()
                        .minusDays(1);

        LocalDate startDate =
                endDate.minusYears(1)
                        .plusDays(1);

        Map<String, Long> result =
                new LinkedHashMap<>();

        for (
                String kdcCode :
                KDC_CODES
        ) {
            URI uri =
                    baseUri(
                            "/loanItemSrch"
                    )
                            .queryParam(
                                    "authKey",
                                    authKey
                            )
                            .queryParam(
                                    "format",
                                    "json"
                            )
                            .queryParam(
                                    "startDt",
                                    startDate
                            )
                            .queryParam(
                                    "endDt",
                                    endDate
                            )
                            .queryParam(
                                    "kdc",
                                    kdcCode
                            )
                            .queryParam(
                                    "pageNo",
                                    1
                            )
                            .queryParam(
                                    "pageSize",
                                    Math.max(
                                            1,
                                            Math.min(
                                                    nationalLoanPageSize,
                                                    200
                                            )
                                    )
                            )
                            .build()
                            .encode()
                            .toUri();

            try {
                JsonNode response =
                        requestJson(
                                uri,
                                "전국 KDC "
                                        + kdcCode
                                        + " 대출 수요"
                        );

                long loanCount =
                        extractDocumentNodes(
                                response.path("docs")
                        )
                                .stream()
                                .mapToLong(
                                        node ->
                                                firstLong(
                                                        node,
                                                        0L,
                                                        "loan_count",
                                                        "loanCount",
                                                        "loanCnt"
                                                )
                                )
                                .sum();

                result.put(
                        kdcCode,
                        loanCount
                );
            } catch (Exception exception) {
                log.warn(
                        "[Data4LibraryAnalyticsClient] 전국 KDC 대출 수요 조회 실패. "
                                + "해당 분류를 0으로 처리합니다. kdc={}",
                        kdcCode,
                        exception
                );

                result.put(
                        kdcCode,
                        0L
                );
            }
        }

        return result;
    }

    private LibraryInfo fetchLibraryInfo(
            String libraryCode
    ) {
        URI uri =
                baseUri("/libSrch")
                        .queryParam(
                                "authKey",
                                authKey
                        )
                        .queryParam(
                                "format",
                                "json"
                        )
                        .queryParam(
                                "libCode",
                                libraryCode
                        )
                        .queryParam(
                                "pageNo",
                                1
                        )
                        .queryParam(
                                "pageSize",
                                1
                        )
                        .build()
                        .encode()
                        .toUri();

        JsonNode response =
                requestJson(
                        uri,
                        "도서관 기본정보"
                );

        List<JsonNode> libraries =
                extractLibraryNodes(
                        response.path("libs")
                );

        if (libraries.isEmpty()) {
            return new LibraryInfo(
                    libraryCode,
                    null,
                    0L
            );
        }

        JsonNode library =
                libraries.get(0);

        return new LibraryInfo(
                firstText(
                        library,
                        libraryCode,
                        "libCode",
                        "lib_code"
                ),

                firstText(
                        library,
                        null,
                        "libName",
                        "libraryName",
                        "name"
                ),

                firstLong(
                        library,
                        0L,
                        "BookCount",
                        "bookCount",
                        "book_count"
                )
        );
    }

    private CollectionAggregate
    fetchCollectionAggregate(
            String libraryCode
    ) {
        int requestedPageSize =
                Math.max(
                        1,
                        Math.min(
                                itemPageSize,
                                100
                        )
                );

        CollectionPage firstPage =
                fetchCollectionPage(
                        libraryCode,
                        1,
                        requestedPageSize
                );

        int actualPageSize =
                Math.max(
                        1,
                        firstPage.pageSize()
                );

        int totalPages =
                Math.max(
                        1,
                        (int) Math.ceil(
                                firstPage.numFound()
                                        / (double) actualPageSize
                        )
                );

        int pagesToRead =
                Math.min(
                        totalPages,
                        Math.max(
                                1,
                                maxItemPages
                        )
                );

        Map<String, Long> genreHoldings =
                emptyKdcMap();

        AggregateAccumulator accumulator =
                new AggregateAccumulator();

        accumulateCollectionPage(
                firstPage.documents(),
                genreHoldings,
                accumulator
        );

        for (
                int page = 2;
                page <= pagesToRead;
                page++
        ) {
            CollectionPage nextPage =
                    fetchCollectionPage(
                            libraryCode,
                            page,
                            requestedPageSize
                    );

            accumulateCollectionPage(
                    nextPage.documents(),
                    genreHoldings,
                    accumulator
            );

            if (
                    nextPage.documents()
                            .isEmpty()
            ) {
                break;
            }
        }

        return new CollectionAggregate(
                Math.max(
                        accumulator.holdingCount,
                        firstPage.numFound()
                ),
                accumulator.loanCount,
                genreHoldings,
                pagesToRead >= totalPages
        );
    }

    private CollectionPage fetchCollectionPage(
            String libraryCode,
            int pageNo,
            int pageSize
    ) {
        URI uri =
                baseUri("/itemSrch")
                        .queryParam(
                                "authKey",
                                authKey
                        )
                        .queryParam(
                                "format",
                                "json"
                        )
                        .queryParam(
                                "type",
                                "ALL"
                        )
                        .queryParam(
                                "libCode",
                                libraryCode
                        )
                        .queryParam(
                                "pageNo",
                                pageNo
                        )
                        .queryParam(
                                "pageSize",
                                pageSize
                        )
                        .build()
                        .encode()
                        .toUri();

        JsonNode response =
                requestJson(
                        uri,
                        "도서관 장서 "
                                + pageNo
                                + "페이지"
                );

        return new CollectionPage(
                firstLong(
                        response,
                        0L,
                        "numFound"
                ),

                (int) firstLong(
                        response,
                        pageSize,
                        "pageSize"
                ),

                extractDocumentNodes(
                        response.path("docs")
                )
        );
    }

    private List<UsagePoint> fetchUsageTrend(
            String libraryCode,
            String type
    ) {
        URI uri =
                baseUri("/usageTrend")
                        .queryParam(
                                "authKey",
                                authKey
                        )
                        .queryParam(
                                "format",
                                "json"
                        )
                        .queryParam(
                                "libCode",
                                libraryCode
                        )
                        .queryParam(
                                "type",
                                type
                        )
                        .build()
                        .encode()
                        .toUri();

        JsonNode response =
                requestJson(
                        uri,
                        "도서관 "
                                + (
                                "D".equals(type)
                                ? "요일별"
                                : "시간대별"
                        )
                                + " 대출반납 추이"
                );

        List<JsonNode> resultNodes =
                extractResultNodes(
                        response.path("results")
                );

        List<UsagePoint> points =
                new ArrayList<>();

        for (
                JsonNode result :
                resultNodes
        ) {
            String rawLabel =
                    "D".equals(type)
                            ? firstText(
                                    result,
                                    "-",
                                    "dayOfWeek",
                                    "day",
                                    "label"
                            )
                            : firstText(
                                    result,
                                    "-",
                                    "hour",
                                    "time",
                                    "label"
                            );

            points.add(
                    new UsagePoint(
                            "D".equals(type)
                                    ? translateDay(
                                            rawLabel
                                    )
                                    : formatHour(
                                            rawLabel
                                    ),

                            firstDouble(
                                    result,
                                    0D,
                                    "loan",
                                    "loanCount",
                                    "loan_count"
                            ),

                            firstDouble(
                                    result,
                                    0D,
                                    "return",
                                    "returnCount",
                                    "return_count"
                            )
                    )
            );
        }

        return points;
    }

    private JsonNode requestJson(
            URI uri,
            String operationName
    ) {
        try {
            log.info(
                    "[Data4LibraryAnalyticsClient] {} 요청: {}",
                    operationName,
                    maskAuthKey(
                            uri.toString()
                    )
            );

            String responseBody =
                    restClient
                            .get()
                            .uri(uri)
                            .retrieve()
                            .body(
                                    String.class
                            );

            if (
                    responseBody == null
                            || responseBody.isBlank()
            ) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        operationName
                                + " 응답이 비어 있습니다."
                );
            }

            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            JsonNode response =
                    root.path("response");

            if (
                    response.isMissingNode()
                            || response.isNull()
            ) {
                response = root;
            }

            String errorCode =
                    firstText(
                            response,
                            null,
                            "errorCode",
                            "errCode",
                            "resultCode"
                    );

            String errorMessage =
                    firstText(
                            response,
                            null,
                            "errorMessage",
                            "errMsg"
                    );

            JsonNode errorNode =
                    response.path("error");

            if (
                    errorNode.isTextual()
                            && StringUtils.hasText(
                            errorNode.asText()
                    )
                            && !"0".equals(
                            errorNode.asText()
                    )
            ) {
                errorMessage =
                        errorNode.asText();
            }

            boolean failedByCode =
                    StringUtils.hasText(
                            errorCode
                    )
                            && !List.of(
                            "0",
                            "00",
                            "SUCCESS",
                            "OK"
                    ).contains(
                            errorCode.toUpperCase()
                    );

            if (
                    failedByCode
                            || StringUtils.hasText(
                            errorMessage
                    )
            ) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        operationName
                                + " 실패"
                                + (
                                StringUtils.hasText(
                                        errorCode
                                )
                                        ? " ["
                                        + errorCode
                                        + "]"
                                        : ""
                        )
                                + (
                                StringUtils.hasText(
                                        errorMessage
                                )
                                        ? ": "
                                        + errorMessage
                                        : ""
                        )
                );
            }

            log.debug(
                    "[Data4LibraryAnalyticsClient] {} 응답 미리보기: {}",
                    operationName,
                    responseBody.length() > 500
                            ? responseBody.substring(
                            0,
                            500
                    )
                            : responseBody
            );

            return response;
        } catch (
                ResponseStatusException exception
        ) {
            throw exception;
        } catch (Exception exception) {
            log.error(
                    "[Data4LibraryAnalyticsClient] {} 실패",
                    operationName,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    operationName
                            + " 조회에 실패했습니다.",
                    exception
            );
        }
    }

    private void accumulateCollectionPage(
            List<JsonNode> documents,
            Map<String, Long> genreHoldings,
            AggregateAccumulator accumulator
    ) {
        for (
                JsonNode document :
                documents
        ) {
            long holdingCount =
                    firstLong(
                            document,
                            1L,
                            "book_count",
                            "bookCount"
                    );

            if (holdingCount <= 0) {
                holdingCount = 1L;
            }

            long loanCount =
                    firstLong(
                            document,
                            0L,
                            "loan_count",
                            "loanCount",
                            "loanCnt"
                    );

            String kdcCode =
                    extractKdcCode(
                            firstText(
                                    document,
                                    "",
                                    "class_no",
                                    "classNo",
                                    "kdc"
                            )
                    );

            accumulator.holdingCount +=
                    holdingCount;

            accumulator.loanCount +=
                    Math.max(
                            0L,
                            loanCount
                    );

            genreHoldings.merge(
                    kdcCode,
                    holdingCount,
                    Long::sum
            );
        }
    }

    private Map<String, Long> emptyKdcMap() {
        Map<String, Long> result =
                new LinkedHashMap<>();

        for (
                String code :
                KDC_CODES
        ) {
            result.put(
                    code,
                    0L
            );
        }

        result.put(
                "X",
                0L
        );

        return result;
    }

    private String extractKdcCode(
            String classNumber
    ) {
        if (
                classNumber == null
                        || classNumber.isBlank()
        ) {
            return "X";
        }

        for (
                int index = 0;
                index < classNumber.length();
                index++
        ) {
            char current =
                    classNumber.charAt(
                            index
                    );

            if (
                    current >= '0'
                            && current <= '9'
            ) {
                return String.valueOf(
                        current
                );
            }
        }

        return "X";
    }

    private List<JsonNode>
    extractDocumentNodes(
            JsonNode node
    ) {
        List<JsonNode> result =
                new ArrayList<>();

        collectNestedNodes(
                node,
                result,
                "doc"
        );

        return result;
    }

    private List<JsonNode>
    extractLibraryNodes(
            JsonNode node
    ) {
        List<JsonNode> result =
                new ArrayList<>();

        collectNestedNodes(
                node,
                result,
                "lib"
        );

        return result;
    }

    private List<JsonNode>
    extractResultNodes(
            JsonNode node
    ) {
        List<JsonNode> result =
                new ArrayList<>();

        collectNestedNodes(
                node,
                result,
                "result"
        );

        return result;
    }

    private void collectNestedNodes(
            JsonNode node,
            List<JsonNode> result,
            String wrapperName
    ) {
        if (
                node == null
                        || node.isMissingNode()
                        || node.isNull()
        ) {
            return;
        }

        if (node.isArray()) {
            for (
                    JsonNode item :
                    node
            ) {
                collectNestedNodes(
                        item,
                        result,
                        wrapperName
                );
            }

            return;
        }

        if (!node.isObject()) {
            return;
        }

        JsonNode wrapped =
                node.path(
                        wrapperName
                );

        if (
                !wrapped.isMissingNode()
                        && !wrapped.isNull()
        ) {
            collectNestedNodes(
                    wrapped,
                    result,
                    wrapperName
            );

            return;
        }

        result.add(node);
    }

    private UriComponentsBuilder baseUri(
            String endpoint
    ) {
        String normalizedBaseUrl =
                baseUrl == null
                        ? ""
                        : baseUrl.replaceAll(
                                "/+$",
                                ""
                        );

        return UriComponentsBuilder
                .fromUriString(
                        normalizedBaseUrl
                                + endpoint
                );
    }

    private String firstText(
            JsonNode node,
            String fallback,
            String... names
    ) {
        for (
                String name :
                names
        ) {
            JsonNode value =
                    node.path(name);

            if (
                    value.isMissingNode()
                            || value.isNull()
            ) {
                continue;
            }

            String text =
                    value.asText();

            if (
                    StringUtils.hasText(
                            text
                    )
            ) {
                return text.trim();
            }
        }

        return fallback;
    }

    private long firstLong(
            JsonNode node,
            long fallback,
            String... names
    ) {
        for (
                String name :
                names
        ) {
            JsonNode value =
                    node.path(name);

            if (
                    value.isMissingNode()
                            || value.isNull()
            ) {
                continue;
            }

            if (value.isNumber()) {
                return value.asLong();
            }

            String text =
                    value.asText();

            if (
                    !StringUtils.hasText(
                            text
                    )
            ) {
                continue;
            }

            try {
                return Long.parseLong(
                        text.replaceAll(
                                "[^0-9-]",
                                ""
                        )
                );
            } catch (
                    NumberFormatException ignored
            ) {
                // 다음 후보 필드 확인
            }
        }

        return fallback;
    }

    private double firstDouble(
            JsonNode node,
            double fallback,
            String... names
    ) {
        for (
                String name :
                names
        ) {
            JsonNode value =
                    node.path(name);

            if (
                    value.isMissingNode()
                            || value.isNull()
            ) {
                continue;
            }

            if (value.isNumber()) {
                return value.asDouble();
            }

            String text =
                    value.asText();

            if (
                    !StringUtils.hasText(
                            text
                    )
            ) {
                continue;
            }

            try {
                return Double.parseDouble(
                        text.replace(
                                ",",
                                ""
                        )
                );
            } catch (
                    NumberFormatException ignored
            ) {
                // 다음 후보 필드 확인
            }
        }

        return fallback;
    }

    private String translateDay(
            String value
    ) {
        return switch (
                value.toLowerCase()
        ) {
            case "monday" -> "월요일";
            case "tuesday" -> "화요일";
            case "wednesday" -> "수요일";
            case "thursday" -> "목요일";
            case "friday" -> "금요일";
            case "saturday" -> "토요일";
            case "sunday" -> "일요일";
            default -> value;
        };
    }

    private String formatHour(
            String value
    ) {
        if (
                "00".equals(value)
                        || "0".equals(value)
        ) {
            return "00~09시";
        }

        if (
                "20".equals(value)
        ) {
            return "20~23시";
        }

        try {
            int hour =
                    Integer.parseInt(
                            value
                    );

            return String.format(
                    "%02d~%02d시",
                    hour,
                    hour + 1
            );
        } catch (
                NumberFormatException ignored
        ) {
            return value;
        }
    }

    private void validateConfiguration() {
        if (
                !StringUtils.hasText(
                        authKey
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "정보나루 인증키가 설정되지 않았습니다."
            );
        }
    }

    private String maskAuthKey(
            String url
    ) {
        if (
                !StringUtils.hasText(
                        authKey
                )
        ) {
            return url;
        }

        return url.replace(
                authKey,
                "****"
        );
    }

    public record LibraryApiBundle(
            LibraryInfo libraryInfo,
            CollectionAggregate collection,
            List<UsagePoint> dayTrend,
            List<UsagePoint> hourTrend
    ) {
    }

    public record LibraryInfo(
            String libraryCode,
            String libraryName,
            long bookCount
    ) {
    }

    public record CollectionAggregate(
            long holdingCount,
            long cumulativeLoanCount,
            Map<String, Long> genreHoldings,
            boolean complete
    ) {
    }

    private record CollectionPage(
            long numFound,
            int pageSize,
            List<JsonNode> documents
    ) {
    }

    private static class AggregateAccumulator {
        private long holdingCount;
        private long loanCount;
    }
}
