package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.NaverBookResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class AladinBookSearchService {

    @Value("${external.aladin.base-url:https://www.aladin.co.kr/ttb/api}")
    private String baseUrl;

    @Value("${external.aladin.ttb-key}")
    private String ttbKey;

    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.create();

    public List<NaverBookResponse> searchBooks(
            String keyword,
            String title,
            String author,
            String isbn13,
            String publisher,
            Integer pageNo,
            Integer pageSize
    ) {

        validateConfig();

        if (StringUtils.hasText(isbn13)) {
            return lookupBookByIsbn(isbn13);
        }

        return searchByConditions(
                keyword,
                title,
                author,
                publisher,
                pageNo,
                pageSize
        );
    }

    public List<NaverBookResponse> lookupBookByIsbn(
            String isbn
    ) {

        validateConfig();

        String normalizedIsbn = normalizeLookupIsbn(isbn);

        String normalizedBaseUrl =
                normalizeBaseUrl(baseUrl);

        String itemIdType =
                normalizedIsbn.length() == 13
                        ? "ISBN13"
                        : "ISBN";

        URI uri = UriComponentsBuilder
                .fromUriString(
                        normalizedBaseUrl + "/ItemLookUp.aspx"
                )
                .queryParam("ttbkey", ttbKey)
                .queryParam("itemIdType", itemIdType)
                .queryParam("ItemId", normalizedIsbn)
                .queryParam("Cover", "Big")
                .queryParam("output", "js")
                .queryParam("Version", "20131101")
                .build()
                .encode()
                .toUri();

        log.info(
                "[알라딘 ISBN 단건 조회 요청] isbn={}, itemIdType={}",
                normalizedIsbn,
                itemIdType
        );

        return requestAladinItems(
                uri,
                "ISBN 단건 조회"
        );
    }

    // ISBN이 없는 일반 도서 검색을 처리

    private List<NaverBookResponse> searchByConditions(
            String keyword,
            String title,
            String author,
            String publisher,
            Integer pageNo,
            Integer pageSize
    ) {

        String query = buildQuery(
                keyword,
                title,
                author,
                publisher
        );

        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException(
                    "검색어가 필요합니다."
            );
        }

        String queryType = resolveQueryType(
                keyword,
                title,
                author,
                publisher
        );

        int safePageNo =
                pageNo == null || pageNo < 1
                        ? 1
                        : pageNo;

        /*
         * 알라딘 API의 한 페이지 조회 개수를
         * 최소 1개, 최대 50개로 제한합니다.
         */
        int safePageSize =
                pageSize == null
                        ? 10
                        : Math.max(
                        1,
                        Math.min(pageSize, 50)
                );

        String normalizedBaseUrl =
                normalizeBaseUrl(baseUrl);

        URI uri = UriComponentsBuilder
                .fromUriString(
                        normalizedBaseUrl + "/ItemSearch.aspx"
                )
                .queryParam("ttbkey", ttbKey)
                .queryParam("Query", query)
                .queryParam("QueryType", queryType)
                .queryParam("MaxResults", safePageSize)
                .queryParam("start", safePageNo)
                .queryParam("SearchTarget", "Book")
                .queryParam("Sort", "Accuracy")
                .queryParam("Cover", "Big")
                .queryParam("output", "js")
                .queryParam("Version", "20131101")
                .queryParam("outofStockfilter", "0")
                .build()
                .encode()
                .toUri();

        log.info(
                "[알라딘 일반 검색 요청] query={}, queryType={}, pageNo={}, pageSize={}",
                query,
                queryType,
                safePageNo,
                safePageSize
        );

        return requestAladinItems(
                uri,
                "일반 도서 검색"
        );
    }

    // 알라딘 API를 호출하고 도서 목록으로 변환
    private List<NaverBookResponse> requestAladinItems(
            URI uri,
            String requestType
    ) {

        try {
            String responseBody = restClient
                    .get()
                    .uri(uri)
                    .header(
                            HttpHeaders.ACCEPT,
                            "application/json"
                    )
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) {
                log.warn(
                        "[알라딘 {}] 응답 본문이 비어 있습니다.",
                        requestType
                );

                return List.of();
            }

            JsonNode root =
                    objectMapper.readTree(responseBody);

            validateAladinResponse(
                    root,
                    requestType
            );

            JsonNode itemNode = root.path("item");

            List<NaverBookResponse> result =
                    new ArrayList<>();
            addItems(
                    result,
                    itemNode
            );

            log.info(
                    "[알라딘 {} 결과] totalResults={}, startIndex={}, itemsPerPage={}, parsed={}",
                    requestType,
                    root.path("totalResults").asInt(0),
                    root.path("startIndex").asInt(0),
                    root.path("itemsPerPage").asInt(0),
                    result.size()
            );

            return result;

        } catch (Exception e) {
            log.error(
                    "[알라딘 {} 실패]",
                    requestType,
                    e
            );

            throw new RuntimeException(
                    "알라딘 "
                            + requestType
                            + " API 호출 실패: "
                            + e.getMessage(),
                    e
            );
        }
    }

    // 알라딘 응답에 오류 정보가 포함되어 있는지 검사
    private void validateAladinResponse(
            JsonNode root,
            String requestType
    ) {

        String errorCode = firstText(
                root,
                "errorCode",
                "ErrorCode"
        );

        String errorMessage = firstText(
                root,
                "errorMessage",
                "ErrorMessage",
                "error"
        );

        if (StringUtils.hasText(errorCode)
                || StringUtils.hasText(errorMessage)) {

            throw new IllegalStateException(
                    "알라딘 "
                            + requestType
                            + " API 오류: "
                            + defaultText(
                            errorCode,
                            "오류 코드 없음"
                    )
                            + " / "
                            + defaultText(
                            errorMessage,
                            "오류 메시지 없음"
                    )
            );
        }
    }

    private void addItems(
            List<NaverBookResponse> result,
            JsonNode node
    ) {

        if (node == null
                || node.isMissingNode()
                || node.isNull()) {

            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                addItems(
                        result,
                        item
                );
            }

            return;
        }

        if (node.isObject()) {
            result.add(
                    toResponse(node)
            );
        }
    }

    private NaverBookResponse toResponse(
            JsonNode item
    ) {

        String rawPubDate = firstText(
                item,
                "pubDate",
                "pubdate"
        );

        String publishedDate =
                normalizeDate(rawPubDate);

        String publicationYear =
                publishedDate != null
                        && publishedDate.length() >= 4
                        ? publishedDate.substring(0, 4)
                        : extractYear(rawPubDate);

        String isbn13 = cleanIsbn(
                firstText(
                        item,
                        "isbn13",
                        "ISBN13"
                )
        );


        if (!StringUtils.hasText(isbn13)) {
            isbn13 = cleanIsbn(
                    firstText(
                            item,
                            "isbn",
                            "ISBN"
                    )
            );
        }

        String categoryName = cleanHtml(
                firstText(
                        item,
                        "categoryName",
                        "category"
                )
        );

        Integer priceSales = integer(
                item,
                "priceSales",
                "pricesales"
        );

        Integer priceStandard = integer(
                item,
                "priceStandard",
                "pricestandard"
        );

        Integer salesPoint = integer(
                item,
                "salesPoint",
                "salespoint"
        );

        log.info(
                "[알라딘 도서 가격 파싱] isbn={}, priceSales={}, priceStandard={}, salesPoint={}",
                isbn13,
                priceSales,
                priceStandard,
                salesPoint
        );

        return NaverBookResponse.builder()
                .title(
                        cleanHtml(
                                firstText(
                                        item,
                                        "title"
                                )
                        )
                )
                .author(
                        cleanHtml(
                                firstText(
                                        item,
                                        "author"
                                )
                        )
                )
                .publisher(
                        cleanHtml(
                                firstText(
                                        item,
                                        "publisher"
                                )
                        )
                )
                .publicationYear(
                        publicationYear
                )
                .publishedDate(
                        publishedDate
                )
                .isbn13(
                        isbn13
                )
                .classNo(
                        null
                )
                .className(
                        StringUtils.hasText(categoryName)
                                ? categoryName
                                : "알라딘 도서"
                )
                .imageUrl(
                        firstText(
                                item,
                                "cover"
                        )
                )
                .detailUrl(
                        firstText(
                                item,
                                "link"
                        )
                )
                .loanCount(
                        0
                )
                .description(
                        cleanHtml(
                                firstText(
                                        item,
                                        "description"
                                )
                        )
                )
                .source(
                        "ALADIN"
                )
                .priceSales(
                        priceSales
                )
                .priceStandard(
                        priceStandard
                )
                .salesPoint(
                        salesPoint
                )
                .build();
    }

    private String resolveQueryType(
            String keyword,
            String title,
            String author,
            String publisher
    ) {

        int conditionCount = 0;

        conditionCount +=
                StringUtils.hasText(keyword)
                        ? 1
                        : 0;

        conditionCount +=
                StringUtils.hasText(title)
                        ? 1
                        : 0;

        conditionCount +=
                StringUtils.hasText(author)
                        ? 1
                        : 0;

        conditionCount +=
                StringUtils.hasText(publisher)
                        ? 1
                        : 0;

        // 여러 조건이 동시에 입력된 경우에는 통합 키워드 검색으로 처리
        if (conditionCount != 1) {
            return "Keyword";
        }

        if (StringUtils.hasText(title)) {
            return "Title";
        }

        if (StringUtils.hasText(author)) {
            return "Author";
        }

        if (StringUtils.hasText(publisher)) {
            return "Publisher";
        }

        return "Keyword";
    }

    // 일반 검색조건을 하나의 검색문으로 조합
    private String buildQuery(
            String keyword,
            String title,
            String author,
            String publisher
    ) {

        return Stream.of(
                        cleanSearchText(title),
                        cleanSearchText(author),
                        cleanSearchText(publisher),
                        cleanSearchText(keyword)
                )
                .filter(StringUtils::hasText)
                .distinct()
                .collect(
                        Collectors.joining(" ")
                );
    }

    private String normalizeLookupIsbn(
            String isbn
    ) {

        String normalizedIsbn =
                cleanIsbn(isbn);

        if (!StringUtils.hasText(normalizedIsbn)) {
            throw new IllegalArgumentException(
                    "ISBN이 필요합니다."
            );
        }

        if (normalizedIsbn.length() != 10
                && normalizedIsbn.length() != 13) {

            throw new IllegalArgumentException(
                    "ISBN은 10자리 또는 13자리여야 합니다. 입력값: "
                            + normalizedIsbn
            );
        }

        return normalizedIsbn;
    }

    private String normalizeBaseUrl(
            String value
    ) {

        return value
                .trim()
                .replaceAll("/+$", "");
    }

    private String cleanSearchText(
            String value
    ) {

        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private String normalizeDate(
            String value
    ) {

        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.matches(
                "\\d{4}-\\d{2}-\\d{2}"
        )) {
            return trimmed;
        }

        if (trimmed.matches(
                "\\d{4}\\.\\d{2}\\.\\d{2}"
        )) {
            return trimmed.replace(
                    '.',
                    '-'
            );
        }

        if (trimmed.matches(
                "\\d{4}/\\d{2}/\\d{2}"
        )) {
            return trimmed.replace(
                    '/',
                    '-'
            );
        }

        String digits =
                trimmed.replaceAll(
                        "[^0-9]",
                        ""
                );

        if (digits.length() == 8) {
            return digits.substring(0, 4)
                    + "-"
                    + digits.substring(4, 6)
                    + "-"
                    + digits.substring(6, 8);
        }

        if (digits.length() == 4) {
            return digits + "-01-01";
        }

        return null;
    }

    private String extractYear(
            String value
    ) {

        if (!StringUtils.hasText(value)) {
            return null;
        }

        String digits = value.replaceAll(
                "[^0-9]",
                ""
        );

        return digits.length() >= 4
                ? digits.substring(0, 4)
                : null;
    }

    private String cleanIsbn(
            String value
    ) {

        if (!StringUtils.hasText(value)) {
            return null;
        }

        String cleaned = value
                .replaceAll(
                        "[^0-9Xx]",
                        ""
                )
                .toUpperCase();

        return StringUtils.hasText(cleaned)
                ? cleaned
                : null;
    }

    private String cleanHtml(
            String value
    ) {

        if (!StringUtils.hasText(value)) {
            return null;
        }

        String withoutTags =
                value.replaceAll(
                        "<[^>]*>",
                        ""
                );

        return HtmlUtils
                .htmlUnescape(withoutTags)
                .trim();
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
            JsonNode valueNode =
                    node.get(fieldName);

            if (valueNode == null
                    || valueNode.isNull()) {

                continue;
            }

            String value =
                    valueNode.asText();

            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private Integer integer(
            JsonNode node,
            String... fieldNames
    ) {

        String value = firstText(
                node,
                fieldNames
        );

        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            String numberText =
                    value.replaceAll(
                            "[^0-9-]",
                            ""
                    );

            if (!StringUtils.hasText(numberText)
                    || "-".equals(numberText)) {

                return null;
            }

            return Integer.valueOf(numberText);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String defaultText(
            String value,
            String defaultValue
    ) {

        return StringUtils.hasText(value)
                ? value
                : defaultValue;
    }

    private void validateConfig() {

        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException(
                    "external.aladin.base-url 설정이 없습니다."
            );
        }

        if (!StringUtils.hasText(ttbKey)) {
            throw new IllegalStateException(
                    "external.aladin.ttb-key 설정이 없습니다."
            );
        }
    }
}