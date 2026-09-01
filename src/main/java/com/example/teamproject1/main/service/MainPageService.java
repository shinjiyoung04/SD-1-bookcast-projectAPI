package com.example.teamproject1.main.service;

import com.example.teamproject1.data4library.Data4LibraryCachedClient;
import com.example.teamproject1.main.dto.MainHotTrendBookResponse;
import com.example.teamproject1.main.dto.MainPopularBookResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class MainPageService {

    private static final String GOYANG_REGION_CODE = "31";
    private static final String GOYANG_DETAIL_REGION_CODES =
            "31101;31103;31104";

    @Value("${data4library.base-url:http://data4library.kr/api}")
    private String baseUrl;

    @Value("${data4library.auth-key:}")
    private String authKey;

    private final ObjectMapper objectMapper;
    private final Data4LibraryCachedClient data4LibraryClient;

    public MainPageService(
            ObjectMapper objectMapper,
            Data4LibraryCachedClient data4LibraryClient
    ) {
        this.objectMapper = objectMapper;
        this.data4LibraryClient = data4LibraryClient;
    }

    public List<MainPopularBookResponse> getPopularBooks(
            Integer limit
    ) {
        int safeLimit = limit == null
                ? 20
                : Math.max(1, Math.min(limit, 20));

        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(29);

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(apiUrl("/loanItemSrch"))
                    .queryParam("authKey", authKey)
                    .queryParam("format", "json")
                    .queryParam("startDt", startDate)
                    .queryParam("endDt", endDate)
                    .queryParam("region", GOYANG_REGION_CODE)
                    .queryParam("dtl_region", GOYANG_DETAIL_REGION_CODES)
                    .queryParam("pageNo", 1)
                    .queryParam("pageSize", safeLimit)
                    .build()
                    .encode()
                    .toUri();

            log.info(
                    "[MainPageService] 정보나루 인기대출도서 조회: {}",
                    maskAuthKey(uri.toString())
            );

            return parsePopularBooks(
                    data4LibraryClient.get(uri),
                    safeLimit,
                    startDate,
                    endDate
            );

        } catch (Exception exception) {
            log.warn(
                    "[MainPageService] 인기대출도서 일시 중단. 빈 목록으로 응답합니다. message={}",
                    exception.getMessage()
            );

            return List.of();
        }
    }

    // 급상승 API는 어제부터 최대 3일을 확인
    public List<MainHotTrendBookResponse> getHotTrendBooks(
            Integer limit
    ) {
        int safeLimit = limit == null
                ? 15
                : Math.max(1, Math.min(limit, 15));

        for (int daysAgo = 1; daysAgo <= 3; daysAgo++) {
            LocalDate searchDate = LocalDate.now().minusDays(daysAgo);

            try {
                URI uri = UriComponentsBuilder
                        .fromUriString(apiUrl("/hotTrend"))
                        .queryParam("authKey", authKey)
                        .queryParam("format", "json")
                        .queryParam("searchDt", searchDate)
                        .build()
                        .encode()
                        .toUri();

                log.info(
                        "[MainPageService] 정보나루 대출급상승 조회: {}",
                        maskAuthKey(uri.toString())
                );

                List<MainHotTrendBookResponse> results =
                        parseHotTrendBooks(
                                data4LibraryClient.get(uri),
                                safeLimit
                        );

                if (!results.isEmpty()) {
                    return results;
                }

            } catch (Exception exception) {
                log.warn(
                        "[MainPageService] 대출급상승 외부 API 실패. 반복 호출을 중단합니다. searchDate={}, message={}",
                        searchDate,
                        exception.getMessage()
                );

                break;
            }
        }

        return List.of();
    }

    private List<MainPopularBookResponse> parsePopularBooks(
            String responseBody,
            int limit,
            LocalDate startDate,
            LocalDate endDate
    ) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        JsonNode response = objectMapper
                .readTree(responseBody)
                .path("response");

        validateResponse(response);

        List<MainPopularBookResponse> results =
                new ArrayList<>();

        collectPopularBooks(
                response.path("docs"),
                results,
                limit,
                startDate,
                endDate
        );

        return List.copyOf(results);
    }

    private List<MainHotTrendBookResponse> parseHotTrendBooks(
            String responseBody,
            int limit
    ) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        JsonNode response = objectMapper
                .readTree(responseBody)
                .path("response");

        validateResponse(response);

        List<MainHotTrendBookResponse> results =
                new ArrayList<>();

        Set<String> seenKeys =
                new LinkedHashSet<>();

        collectHotTrendBooks(
                response.path("results"),
                results,
                seenKeys,
                limit,
                null
        );

        return List.copyOf(results);
    }

    private void validateResponse(JsonNode response) {
        if (response == null
                || response.isMissingNode()
                || response.isNull()) {
            throw new IllegalStateException(
                    "정보나루 response 노드가 없습니다."
            );
        }

        String errCode = firstText(
                response,
                "errCode",
                "errorCode",
                "resultCode"
        );

        String error = firstText(
                response,
                "error",
                "errMsg",
                "errorMessage"
        );

        boolean successCode = !hasText(errCode)
                || "0".equals(errCode)
                || "00".equals(errCode)
                || "200".equals(errCode);

        if (!successCode || hasText(error)) {
            throw new IllegalStateException(
                    "정보나루 응답 오류: "
                            + defaultText(error, errCode)
            );
        }
    }

    private void collectPopularBooks(
            JsonNode node,
            List<MainPopularBookResponse> results,
            int limit,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()
                || results.size() >= limit) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                collectPopularBooks(
                        item,
                        results,
                        limit,
                        startDate,
                        endDate
                );

                if (results.size() >= limit) {
                    return;
                }
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        JsonNode doc = node.path("doc");

        if (!doc.isMissingNode() && !doc.isNull()) {
            collectPopularBooks(
                    doc,
                    results,
                    limit,
                    startDate,
                    endDate
            );
            return;
        }

        String isbn13 = firstText(node, "isbn13", "isbn");
        String title = firstText(node, "bookname", "title");

        if (!hasText(isbn13) && !hasText(title)) {
            return;
        }

        Integer ranking = firstInteger(
                node,
                "ranking",
                "rank",
                "no"
        );

        Integer loanCount = firstInteger(
                node,
                "loan_count",
                "loanCount"
        );

        results.add(
                new MainPopularBookResponse(
                        ranking == null
                                ? results.size() + 1
                                : ranking,
                        defaultText(title, "도서 제목 없음"),
                        defaultText(
                                firstText(node, "authors", "author"),
                                "저자 정보 없음"
                        ),
                        defaultText(
                                firstText(node, "publisher"),
                                "출판사 정보 없음"
                        ),
                        firstText(
                                node,
                                "publication_year",
                                "publicationYear"
                        ),
                        isbn13,
                        defaultText(
                                firstText(node, "class_nm", "className"),
                                "분류 정보 없음"
                        ),
                        firstText(
                                node,
                                "bookImageURL",
                                "bookImageUrl",
                                "imageUrl"
                        ),
                        loanCount == null ? 0 : loanCount,
                        startDate.toString(),
                        endDate.toString()
                )
        );
    }

    private void collectHotTrendBooks(
            JsonNode node,
            List<MainHotTrendBookResponse> results,
            Set<String> seenKeys,
            int limit,
            String inheritedDate
    ) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()
                || results.size() >= limit) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                collectHotTrendBooks(
                        item,
                        results,
                        seenKeys,
                        limit,
                        inheritedDate
                );

                if (results.size() >= limit) {
                    return;
                }
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        String currentDate = defaultText(
                firstText(node, "date"),
                inheritedDate
        );

        for (String wrapper : List.of("result", "docs", "doc")) {
            JsonNode wrapped = node.path(wrapper);

            if (!wrapped.isMissingNode() && !wrapped.isNull()) {
                collectHotTrendBooks(
                        wrapped,
                        results,
                        seenKeys,
                        limit,
                        currentDate
                );
                return;
            }
        }

        String isbn13 = firstText(node, "isbn13", "isbn");
        String title = firstText(node, "bookname", "title");

        if (!hasText(isbn13) && !hasText(title)) {
            return;
        }

        String seenKey = defaultText(currentDate, "")
                + "|"
                + defaultText(isbn13, title);

        if (!seenKeys.add(seenKey)) {
            return;
        }

        results.add(
                new MainHotTrendBookResponse(
                        currentDate,
                        firstInteger(node, "no"),
                        firstInteger(node, "difference"),
                        firstInteger(node, "baseWeekRank"),
                        firstInteger(node, "pastWeekRank"),
                        firstInteger(node, "ranking", "rank"),
                        defaultText(title, "도서 제목 없음"),
                        defaultText(
                                firstText(node, "authors", "author"),
                                "저자 정보 없음"
                        ),
                        defaultText(
                                firstText(node, "publisher"),
                                "출판사 정보 없음"
                        ),
                        firstText(
                                node,
                                "publication_year",
                                "publicationYear"
                        ),
                        isbn13,
                        defaultText(
                                firstText(node, "class_no", "className"),
                                "분류 정보 없음"
                        ),
                        firstText(
                                node,
                                "bookImageURL",
                                "bookImageUrl",
                                "imageUrl"
                        )
                )
        );
    }

    private String firstText(
            JsonNode node,
            String... fieldNames
    ) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);

            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText();

                if (hasText(text)) {
                    return text.trim();
                }
            }
        }

        return null;
    }

    private Integer firstInteger(
            JsonNode node,
            String... fieldNames
    ) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);

            if (value.isMissingNode() || value.isNull()) {
                continue;
            }

            if (value.isInt() || value.isLong()) {
                return value.asInt();
            }

            String text = value.asText();

            if (!hasText(text)) {
                continue;
            }

            try {
                String numeric = text.replaceAll("[^0-9-]", "");

                if (numeric.isBlank() || "-".equals(numeric)) {
                    continue;
                }

                return Integer.parseInt(numeric);
            } catch (NumberFormatException ignored) {
                // 다음 후보 필드를 확인
            }
        }

        return null;
    }

    private String apiUrl(String path) {
        String normalizedBaseUrl = String.valueOf(baseUrl).trim();

        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(
                    0,
                    normalizedBaseUrl.length() - 1
            );
        }

        return normalizedBaseUrl
                + (path.startsWith("/") ? path : "/" + path);
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String maskAuthKey(String url) {
        if (!hasText(authKey)) {
            return url;
        }

        return url.replace(authKey, "****");
    }
}
