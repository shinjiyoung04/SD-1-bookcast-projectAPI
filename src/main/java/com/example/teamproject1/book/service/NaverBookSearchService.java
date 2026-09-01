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
public class NaverBookSearchService {

    private static final String NAVER_BOOK_SEARCH_URL =
            "https://openapi.naver.com/v1/search/book.json";

    @Value("${external.naver.client-id}")
    private String clientId;

    @Value("${external.naver.client-secret}")
    private String clientSecret;

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

        String query = buildQuery(
                keyword,
                title,
                author,
                isbn13,
                publisher
        );

        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("검색어가 필요합니다.");
        }

        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null
                ? 10
                : Math.max(1, Math.min(pageSize, 100));

        long calculatedStart =
                ((long) safePageNo - 1L) * safePageSize + 1L;

        int start = (int) Math.min(calculatedStart, 1000L);

        URI uri = UriComponentsBuilder
                .fromUriString(NAVER_BOOK_SEARCH_URL)
                .queryParam("query", query)
                .queryParam("display", safePageSize)
                .queryParam("start", start)
                .queryParam("sort", "sim")
                .build()
                .encode()
                .toUri();

        log.info(
                "[네이버 도서 검색 요청] query={}, pageNo={}, pageSize={}, start={}",
                query,
                safePageNo,
                safePageSize,
                start
        );

        try {
            String responseBody = restClient
                    .get()
                    .uri(uri)
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) {
                log.warn("[네이버 도서 검색] 응답 본문이 비어 있습니다.");
                return List.of();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode itemsNode = root.path("items");

            if (!itemsNode.isArray()) {
                log.warn("[네이버 도서 검색] items 배열이 없습니다.");
                return List.of();
            }

            List<NaverBookResponse> result = new ArrayList<>();

            for (JsonNode item : itemsNode) {
                result.add(toResponse(item));
            }

            log.info(
                    "[네이버 도서 검색 결과] total={}, start={}, display={}, parsed={}",
                    root.path("total").asInt(0),
                    root.path("start").asInt(0),
                    root.path("display").asInt(0),
                    result.size()
            );

            return result;

        } catch (Exception e) {
            log.error("[네이버 도서 검색 실패]", e);
            throw new RuntimeException(
                    "네이버 도서 검색 API 호출 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private NaverBookResponse toResponse(JsonNode item) {
        String title = cleanHtml(text(item, "title"));
        String author = cleanHtml(text(item, "author"));
        String publisher = cleanHtml(text(item, "publisher"));
        String description = cleanHtml(text(item, "description"));

        String isbn13 = extractIsbn13(text(item, "isbn"));

        String pubdate = onlyDigits(text(item, "pubdate"));

        String publicationYear =
                pubdate != null && pubdate.length() >= 4
                        ? pubdate.substring(0, 4)
                        : null;

        return NaverBookResponse.builder()
                .title(title)
                .author(author)
                .publisher(publisher)
                .publicationYear(publicationYear)
                .publishedDate(toIsoDate(pubdate))
                .isbn13(isbn13)
                .classNo(null)
                .className("네이버 도서")
                .imageUrl(text(item, "image"))
                .detailUrl(text(item, "link"))
                .loanCount(0)
                .description(description)
                .source("NAVER")
                .priceSales(null)
                .priceStandard(null)
                .salesPoint(null)
                .build();
    }

    private String buildQuery(
            String keyword,
            String title,
            String author,
            String isbn13,
            String publisher
    ) {
        return Stream.of(
                        cleanSearchText(isbn13),
                        cleanSearchText(title),
                        cleanSearchText(author),
                        cleanSearchText(publisher),
                        cleanSearchText(keyword)
                )
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(" "));
    }

    private String cleanSearchText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private String extractIsbn13(String rawIsbn) {
        if (!StringUtils.hasText(rawIsbn)) {
            return null;
        }

        String[] tokens = rawIsbn.trim().split("\\s+");

        for (String token : tokens) {
            String cleaned = token.replaceAll("[^0-9Xx]", "");

            if (cleaned.length() == 13) {
                return cleaned;
            }
        }

        for (String token : tokens) {
            String cleaned = token.replaceAll("[^0-9Xx]", "");

            if (cleaned.length() == 10) {
                return cleaned;
            }
        }

        String cleaned = rawIsbn.replaceAll("[^0-9Xx]", "");

        return StringUtils.hasText(cleaned)
                ? cleaned
                : null;
    }

    private String toIsoDate(String pubdate) {
        if (!StringUtils.hasText(pubdate)) {
            return null;
        }

        if (pubdate.length() == 8) {
            return pubdate.substring(0, 4)
                    + "-"
                    + pubdate.substring(4, 6)
                    + "-"
                    + pubdate.substring(6, 8);
        }

        if (pubdate.length() == 4) {
            return pubdate + "-01-01";
        }

        return null;
    }

    private String onlyDigits(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String cleaned = value.replaceAll("[^0-9]", "");

        return StringUtils.hasText(cleaned)
                ? cleaned
                : null;
    }

    private String cleanHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String withoutTags = value.replaceAll("<[^>]*>", "");

        return HtmlUtils
                .htmlUnescape(withoutTags)
                .trim();
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        JsonNode valueNode = node.get(fieldName);

        if (valueNode == null || valueNode.isNull()) {
            return null;
        }

        String value = valueNode.asText();

        return StringUtils.hasText(value)
                ? value.trim()
                : null;
    }

    private void validateConfig() {
        if (!StringUtils.hasText(clientId)) {
            throw new IllegalStateException(
                    "external.naver.client-id 설정이 없습니다."
            );
        }

        if (!StringUtils.hasText(clientSecret)) {
            throw new IllegalStateException(
                    "external.naver.client-secret 설정이 없습니다."
            );
        }
    }
}
