package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.ExternalBookDetailResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
@RequiredArgsConstructor
@Slf4j
public class NaverBookService {

    @Value("${external.naver.client-id}")
    private String clientId;

    @Value("${external.naver.client-secret}")
    private String clientSecret;

    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.create();

    public ExternalBookDetailResponse searchBookDetailByIsbn(String isbn13) {
        try {
            if (!hasText(isbn13)) {
                throw new IllegalArgumentException("ISBN이 비어 있습니다.");
            }

            String cleanIsbn = isbn13.replaceAll("[^0-9Xx]", "");

            URI uri = UriComponentsBuilder
                    .fromUriString("https://openapi.naver.com/v1/search/book.json")
                    .queryParam("query", cleanIsbn)
                    .queryParam("display", 1)
                    .queryParam("start", 1)
                    .build()
                    .encode()
                    .toUri();

            log.info("[네이버 도서 상세 조회 URI] {}", uri);

            String responseBody = restClient
                    .get()
                    .uri(uri)
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .retrieve()
                    .body(String.class);

            logResponsePreview("네이버 도서 상세 조회 응답", responseBody);

            if (responseBody == null || responseBody.isBlank()) {
                return emptyResponse(cleanIsbn);
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.path("items");

            if (!items.isArray() || items.isEmpty()) {
                log.warn("[네이버 도서 상세 조회] 검색 결과가 없습니다. isbn13={}", cleanIsbn);
                return emptyResponse(cleanIsbn);
            }

            JsonNode item = items.get(0);

            String title = cleanHtml(text(item, "title"));
            String author = cleanHtml(text(item, "author"));
            String publisher = cleanHtml(text(item, "publisher"));
            String pubdate = cleanHtml(text(item, "pubdate"));
            String description = cleanHtml(text(item, "description"));
            String imageUrl = text(item, "image");
            String detailUrl = text(item, "link");
            String isbn = text(item, "isbn");

            String parsedIsbn13 = extractIsbn13(isbn, cleanIsbn);

            return new ExternalBookDetailResponse(
                    title,
                    author,
                    publisher,
                    toYear(pubdate),
                    parsedIsbn13,
                    description,
                    imageUrl,
                    detailUrl,
                    "NAVER"
            );

        } catch (Exception e) {
            log.error("[네이버 도서 상세 조회 실패]", e);
            return emptyResponse(isbn13);
        }
    }

    private ExternalBookDetailResponse emptyResponse(String isbn13) {
        return new ExternalBookDetailResponse(
                null,
                null,
                null,
                null,
                isbn13,
                null,
                null,
                null,
                "NAVER"
        );
    }

    private String extractIsbn13(String rawIsbn, String fallback) {
        if (!hasText(rawIsbn)) {
            return fallback;
        }

        String[] tokens = rawIsbn.split("\\s+");

        for (String token : tokens) {
            String cleaned = token.replaceAll("[^0-9Xx]", "");

            if (cleaned.length() == 13) {
                return cleaned;
            }
        }

        String cleanedAll = rawIsbn.replaceAll("[^0-9Xx]", "");

        if (cleanedAll.length() >= 13) {
            return cleanedAll.substring(cleanedAll.length() - 13);
        }

        return fallback;
    }

    private String toYear(String pubdate) {
        if (!hasText(pubdate)) {
            return null;
        }

        String cleaned = pubdate.replaceAll("[^0-9]", "");

        if (cleaned.length() >= 4) {
            return cleaned.substring(0, 4);
        }

        return null;
    }

    private String cleanHtml(String value) {
        if (!hasText(value)) {
            return null;
        }

        String withoutTags = value.replaceAll("<[^>]*>", "");
        String unescaped = HtmlUtils.htmlUnescape(withoutTags);

        return unescaped.isBlank() ? null : unescaped.trim();
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText();

        if (text == null || text.isBlank()) {
            return null;
        }

        return text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void logResponsePreview(String title, String responseBody) {
        if (responseBody == null) {
            log.warn("[{}] null", title);
            return;
        }

        int previewLength = Math.min(responseBody.length(), 1000);
        log.info("[{} 앞부분] {}", title, responseBody.substring(0, previewLength));
    }
}