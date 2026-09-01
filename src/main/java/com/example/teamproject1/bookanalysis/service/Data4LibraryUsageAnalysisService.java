package com.example.teamproject1.bookanalysis.service;

import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse;
import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse.KeywordItemResponse;
import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse.LoanTrendItemResponse;
import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse.PopularGroupItemResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.teamproject1.data4library.Data4LibraryCachedClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class Data4LibraryUsageAnalysisService {

    @Value("${data4library.base-url}")
    private String baseUrl;

    @Value("${data4library.auth-key}")
    private String authKey;

    private final ObjectMapper objectMapper;
    private final Data4LibraryCachedClient data4LibraryClient;

    public Data4LibraryUsageAnalysisService(
            ObjectMapper objectMapper,
            Data4LibraryCachedClient data4LibraryClient
    ) {
        this.objectMapper = objectMapper;
        this.data4LibraryClient = data4LibraryClient;
    }

    public BookUsageAnalysisResponse getUsageAnalysis(
            String isbn
    ) {
        String cleanIsbn =
                normalizeIsbn(isbn);

        URI uri =
                UriComponentsBuilder
                        .fromUriString(
                                baseUrl
                                        + "/usageAnalysisList"
                        )
                        .queryParam(
                                "authKey",
                                authKey
                        )
                        .queryParam(
                                "isbn13",
                                cleanIsbn
                        )
                        .queryParam(
                                "format",
                                "json"
                        )
                        .build()
                        .encode()
                        .toUri();

        log.info(
                "[정보나루 도서별 이용분석 요청] isbn={}, uri={}",
                cleanIsbn,
                maskAuthKey(
                        uri.toString()
                )
        );

        try {
            String responseBody =
                    data4LibraryClient.get(uri);

            if (!StringUtils.hasText(
                    responseBody
            )) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "정보나루 이용분석 응답이 비어 있습니다."
                );
            }

            logResponsePreview(
                    responseBody
            );

            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            JsonNode responseNode =
                    root.path(
                            "response"
                    );

            if (responseNode.isMissingNode()
                    || responseNode.isNull()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "정보나루 이용분석 응답 형식이 올바르지 않습니다."
                );
            }

            validateApiError(
                    responseNode
            );

            JsonNode bookNode =
                    unwrapItemNode(
                            responseNode.path(
                                    "book"
                            ),
                            "book"
                    );

            Integer totalLoanCount =
                    firstInteger(
                            bookNode,
                            "loanCnt",
                            "loan_count"
                    );

            List<LoanTrendItemResponse>
                    loanTrend =
                    parseLoanTrend(
                            responseNode.path(
                                    "loanHistory"
                            )
                    );

            List<PopularGroupItemResponse>
                    popularGroups =
                    parsePopularGroups(
                            responseNode.path(
                                    "loanGrps"
                            )
                    );

            List<KeywordItemResponse>
                    keywords =
                    parseKeywords(
                            responseNode.path(
                                    "keywords"
                            )
                    );

            if (totalLoanCount == null) {
                totalLoanCount =
                        loanTrend.stream()
                                .map(
                                        LoanTrendItemResponse::loanCount
                                )
                                .filter(
                                        value ->
                                                value != null
                                )
                                .mapToInt(
                                        Integer::intValue
                                )
                                .sum();
            }

            BookUsageAnalysisResponse result =
                    new BookUsageAnalysisResponse(
                            cleanIsbn,
                            totalLoanCount,
                            loanTrend,
                            popularGroups,
                            keywords
                    );

            log.info(
                    "[정보나루 도서별 이용분석 완료] isbn={}, totalLoanCount={}, loanTrend={}, popularGroups={}, keywords={}",
                    cleanIsbn,
                    totalLoanCount,
                    loanTrend.size(),
                    popularGroups.size(),
                    keywords.size()
            );

            return result;

        } catch (
                ResponseStatusException exception
        ) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw exception;
            }

            log.warn(
                    "[정보나루 도서별 이용분석 일시 중단] 빈 데이터로 계속 진행합니다. isbn={}, message={}",
                    cleanIsbn,
                    exception.getReason()
            );

            return createUnavailableResponse(cleanIsbn);

        } catch (Exception exception) {
            log.warn(
                    "[정보나루 도서별 이용분석 일시 중단] 빈 데이터로 계속 진행합니다. isbn={}, message={}",
                    cleanIsbn,
                    exception.getMessage()
            );

            return createUnavailableResponse(cleanIsbn);
        }
    }

    private BookUsageAnalysisResponse createUnavailableResponse(
            String isbn
    ) {
        return new BookUsageAnalysisResponse(
                isbn,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public List<KeywordItemResponse> getKeywords(
            String isbn
    ) {
        String cleanIsbn =
                normalizeIsbn(isbn);

        URI uri =
                UriComponentsBuilder
                        .fromUriString(
                                baseUrl
                                        + "/keywordList"
                        )
                        .queryParam(
                                "authKey",
                                authKey
                        )
                        .queryParam(
                                "isbn13",
                                cleanIsbn
                        )
                        .queryParam(
                                "additionalYN",
                                "Y"
                        )
                        .queryParam(
                                "format",
                                "json"
                        )
                        .build()
                        .encode()
                        .toUri();

        log.info(
                "[정보나루 도서 키워드 요청] isbn={}, uri={}",
                cleanIsbn,
                maskAuthKey(
                        uri.toString()
                )
        );

        try {
            String responseBody =
                    data4LibraryClient.get(uri);

            if (!StringUtils.hasText(
                    responseBody
            )) {
                log.warn(
                        "[정보나루 도서 키워드] 응답이 비어 있습니다. isbn={}",
                        cleanIsbn
                );

                return List.of();
            }

            int previewLength =
                    Math.min(
                            responseBody.length(),
                            3000
                    );

            log.info(
                    "[정보나루 도서 키워드 응답 앞부분] {}",
                    responseBody.substring(
                            0,
                            previewLength
                    )
            );

            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            JsonNode responseNode =
                    root.path(
                            "response"
                    );

            if (responseNode.isMissingNode()
                    || responseNode.isNull()) {
                log.warn(
                        "[정보나루 도서 키워드] response 노드가 없습니다. isbn={}",
                        cleanIsbn
                );

                return List.of();
            }

            validateApiError(
                    responseNode
            );

            return parseKeywordListItems(
                    responseNode.path(
                            "items"
                    )
            );

        } catch (
                ResponseStatusException exception
        ) {
            log.warn(
                    "[정보나루 도서 키워드 API 오류] isbn={}, message={}",
                    cleanIsbn,
                    exception.getReason()
            );

            return List.of();

        } catch (Exception exception) {
            log.warn(
                    "[정보나루 도서 키워드 조회 실패] isbn={}, message={}",
                    cleanIsbn,
                    exception.getMessage()
            );

            return List.of();
        }
    }

    private List<LoanTrendItemResponse>
    parseLoanTrend(
            JsonNode loanHistoryNode
    ) {
        List<JsonNode> rawItems =
                collectWrappedItems(
                        loanHistoryNode,
                        "loan"
                );

        List<LoanTrendItemResponse> result =
                new ArrayList<>();

        for (JsonNode item : rawItems) {
            String month =
                    normalizeMonth(
                            firstText(
                                    item,
                                    "month",
                                    "loanMonth"
                            )
                    );

            Integer loanCount =
                    firstInteger(
                            item,
                            "loanCnt",
                            "loan_count",
                            "loanCount"
                    );

            Integer ranking =
                    firstInteger(
                            item,
                            "ranking",
                            "rank"
                    );

            if (!StringUtils.hasText(month)
                    && loanCount == null
                    && ranking == null) {
                continue;
            }

            result.add(
                    new LoanTrendItemResponse(
                            month,
                            defaultZero(
                                    loanCount
                            ),
                            ranking
                    )
            );
        }

        result.sort(
                Comparator.comparing(
                        LoanTrendItemResponse::loanMonth,
                        Comparator.nullsLast(
                                String::compareTo
                        )
                )
        );

        return List.copyOf(
                result
        );
    }

    private List<PopularGroupItemResponse>
    parsePopularGroups(
            JsonNode loanGroupsNode
    ) {
        List<JsonNode> rawItems =
                collectWrappedItems(
                        loanGroupsNode,
                        "loanGrp"
                );

        List<PopularGroupItemResponse> result =
                new ArrayList<>();

        for (JsonNode item : rawItems) {
            String age =
                    normalizeAge(
                            firstText(
                                    item,
                                    "age"
                            )
                    );

            String gender =
                    normalizeGender(
                            firstText(
                                    item,
                                    "gender"
                            )
                    );

            Integer loanCount =
                    firstInteger(
                            item,
                            "loanCnt",
                            "loan_count",
                            "loanCount"
                    );

            Integer ranking =
                    firstInteger(
                            item,
                            "ranking",
                            "rank"
                    );

            if (!StringUtils.hasText(age)
                    && !StringUtils.hasText(
                    gender
            )
                    && loanCount == null
                    && ranking == null) {
                continue;
            }

            result.add(
                    new PopularGroupItemResponse(
                            age,
                            gender,
                            defaultZero(
                                    loanCount
                            ),
                            ranking
                    )
            );
        }

        return List.copyOf(
                result
        );
    }


    /**
     * keywordList 응답 구조:
     * response.items.item
     */
    private List<KeywordItemResponse>
    parseKeywordListItems(
            JsonNode itemsNode
    ) {
        List<JsonNode> rawItems =
                collectWrappedItems(
                        itemsNode,
                        "item"
                );

        List<KeywordItemResponse> result =
                new ArrayList<>();

        for (JsonNode item : rawItems) {
            String word =
                    firstText(
                            item,
                            "word"
                    );

            if (!StringUtils.hasText(
                    word
            )) {
                continue;
            }

            Double weight =
                    firstDouble(
                            item,
                            "weight"
                    );

            result.add(
                    new KeywordItemResponse(
                            word.trim(),
                            weight
                    )
            );
        }

        result.sort(
                Comparator.comparing(
                                KeywordItemResponse::weight,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                        .thenComparing(
                                KeywordItemResponse::word
                        )
        );

        if (result.size() > 12) {
            return List.copyOf(
                    result.subList(
                            0,
                            12
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private List<KeywordItemResponse>
    parseKeywords(
            JsonNode keywordsNode
    ) {
        List<JsonNode> rawItems =
                collectWrappedItems(
                        keywordsNode,
                        "keyword"
                );

        List<KeywordItemResponse> result =
                new ArrayList<>();

        for (JsonNode item : rawItems) {
            String word =
                    firstText(
                            item,
                            "word"
                    );

            if (!StringUtils.hasText(
                    word
            )) {
                continue;
            }

            Double weight =
                    firstDouble(
                            item,
                            "weight"
                    );

            result.add(
                    new KeywordItemResponse(
                            word.trim(),
                            weight
                    )
            );
        }

        result.sort(
                Comparator.comparing(
                                KeywordItemResponse::weight,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                        .thenComparing(
                                KeywordItemResponse::word
                        )
        );

        if (result.size() > 12) {
            return List.copyOf(
                    result.subList(
                            0,
                            12
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private List<JsonNode>
    collectWrappedItems(
            JsonNode container,
            String wrapperName
    ) {
        List<JsonNode> result =
                new ArrayList<>();

        collectWrappedItems(
                container,
                wrapperName,
                result
        );

        return result;
    }

    private void collectWrappedItems(
            JsonNode node,
            String wrapperName,
            List<JsonNode> result
    ) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                collectWrappedItems(
                        item,
                        wrapperName,
                        result
                );
            }

            return;
        }

        if (!node.isObject()) {
            return;
        }

        JsonNode wrapped =
                node.get(
                        wrapperName
                );

        if (wrapped != null
                && !wrapped.isNull()) {
            collectWrappedItems(
                    wrapped,
                    wrapperName,
                    result
            );

            return;
        }

        result.add(node);
    }

    private JsonNode unwrapItemNode(
            JsonNode node,
            String wrapperName
    ) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()) {
            return objectMapper.createObjectNode();
        }

        if (node.isArray()) {
            if (node.isEmpty()) {
                return objectMapper.createObjectNode();
            }

            return unwrapItemNode(
                    node.get(0),
                    wrapperName
            );
        }

        if (node.isObject()) {
            JsonNode wrapped =
                    node.get(
                            wrapperName
                    );

            if (wrapped != null
                    && !wrapped.isNull()) {
                return unwrapItemNode(
                        wrapped,
                        wrapperName
                );
            }
        }

        return node;
    }

    private void validateApiError(
            JsonNode responseNode
    ) {
        String errCode =
                firstText(
                        responseNode,
                        "errCode"
                );

        String error =
                firstText(
                        responseNode,
                        "error",
                        "errMsg"
                );

        if (StringUtils.hasText(
                errCode
        ) || StringUtils.hasText(
                error
        )) {
            String message =
                    StringUtils.hasText(
                            error
                    )
                            ? error
                            : "정보나루 API 오류 코드: "
                            + errCode;

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    message
            );
        }
    }

    private String normalizeIsbn(
            String isbn
    ) {
        if (!StringUtils.hasText(
                isbn
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN이 필요합니다."
            );
        }

        String cleanIsbn =
                isbn.replaceAll(
                        "[^0-9Xx]",
                        ""
                );

        if (cleanIsbn.length() != 10
                && cleanIsbn.length() != 13) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN은 10자리 또는 13자리여야 합니다."
            );
        }

        return cleanIsbn;
    }

    private String normalizeMonth(
            String value
    ) {
        if (!StringUtils.hasText(
                value
        )) {
            return null;
        }

        String normalized =
                value.trim()
                        .replace(
                                "/",
                                "."
                        )
                        .replace(
                                "-",
                                "."
                        );

        if (normalized.matches(
                "\\d{6}"
        )) {
            return normalized.substring(
                    0,
                    4
            )
                    + "."
                    + normalized.substring(
                    4,
                    6
            );
        }

        if (normalized.matches(
                "\\d{4}\\.\\d{1,2}"
        )) {
            String[] parts =
                    normalized.split(
                            "\\."
                    );

            return parts[0]
                    + "."
                    + String.format(
                    "%02d",
                    Integer.parseInt(
                            parts[1]
                    )
            );
        }

        return normalized;
    }

    private String normalizeAge(
            String value
    ) {
        if (!StringUtils.hasText(
                value
        )) {
            return "미상";
        }

        String normalized =
                value.trim();

        return switch (normalized) {
            case "0" -> "영유아";
            case "6" -> "유아";
            case "8" -> "초등";
            case "14" -> "청소년";
            case "20" -> "20대";
            case "30" -> "30대";
            case "40" -> "40대";
            case "50" -> "50대";
            case "60" -> "60대이상";
            case "-1" -> "미상";
            default -> normalized;
        };
    }

    private String normalizeGender(
            String value
    ) {
        if (!StringUtils.hasText(
                value
        )) {
            return "미상";
        }

        String normalized =
                value.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        return switch (normalized) {
            case "0", "M", "MALE", "남" ->
                    "남성";

            case "1", "F", "FEMALE", "여" ->
                    "여성";

            case "2", "UNKNOWN", "미상" ->
                    "미상";

            default -> value.trim();
        };
    }

    private String firstText(
            JsonNode node,
            String... fieldNames
    ) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()) {
            return null;
        }

        for (String fieldName : fieldNames) {
            JsonNode value =
                    node.get(
                            fieldName
                    );

            if (value == null
                    || value.isNull()) {
                continue;
            }

            String text =
                    value.asText();

            if (StringUtils.hasText(
                    text
            )) {
                return text.trim();
            }
        }

        return null;
    }

    private Integer firstInteger(
            JsonNode node,
            String... fieldNames
    ) {
        String value =
                firstText(
                        node,
                        fieldNames
                );

        if (!StringUtils.hasText(
                value
        )) {
            return null;
        }

        String normalized =
                value.replaceAll(
                        "[^0-9-]",
                        ""
                );

        if (!StringUtils.hasText(
                normalized
        ) || "-".equals(
                normalized
        )) {
            return null;
        }

        try {
            return Integer.valueOf(
                    normalized
            );
        } catch (
                NumberFormatException ignored
        ) {
            return null;
        }
    }

    private Double firstDouble(
            JsonNode node,
            String... fieldNames
    ) {
        String value =
                firstText(
                        node,
                        fieldNames
                );

        if (!StringUtils.hasText(
                value
        )) {
            return null;
        }

        String normalized =
                value.replaceAll(
                        "[^0-9.\\-]",
                        ""
                );

        if (!StringUtils.hasText(
                normalized
        )
                || "-".equals(
                normalized
        )
                || ".".equals(
                normalized
        )) {
            return null;
        }

        try {
            return Double.valueOf(
                    normalized
            );
        } catch (
                NumberFormatException ignored
        ) {
            return null;
        }
    }

    private Integer defaultZero(
            Integer value
    ) {
        return value == null
                ? 0
                : value;
    }

    private void logResponsePreview(
            String responseBody
    ) {
        int previewLength =
                Math.min(
                        responseBody.length(),
                        3000
                );

        log.info(
                "[정보나루 도서별 이용분석 응답 앞부분] {}",
                responseBody.substring(
                        0,
                        previewLength
                )
        );
    }

    private String maskAuthKey(
            String uri
    ) {
        if (!StringUtils.hasText(
                uri
        )
                || !StringUtils.hasText(
                authKey
        )) {
            return uri;
        }

        return uri.replace(
                authKey,
                "****"
        );
    }
}
