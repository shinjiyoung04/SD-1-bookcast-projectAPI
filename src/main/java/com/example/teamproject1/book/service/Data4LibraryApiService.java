package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.ExternalBookSearchDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class Data4LibraryApiService {

    @Value("${external.data4library.auth-key}")
    private String authKey;

    public List<ExternalBookSearchDTO> searchPopular(String keyword, int pageNo, int pageSize) {
        RestTemplate restTemplate = new RestTemplate();

        String url = UriComponentsBuilder
                .fromHttpUrl("http://data4library.kr/api/loanItemSrch")
                .queryParam("authKey", authKey)
                .queryParam("pageNo", pageNo)
                .queryParam("pageSize", pageSize)
                .queryParam("format", "json")
                .build()
                .toUriString();

        Map<String, Object> body = restTemplate.getForObject(url, Map.class);

        if (body == null || body.get("response") == null) {
            return List.of();
        }

        Map<String, Object> response = (Map<String, Object>) body.get("response");
        Map<String, Object> docs = (Map<String, Object>) response.get("docs");

        if (docs == null || docs.get("doc") == null) {
            return List.of();
        }

        Object docObj = docs.get("doc");

        List<Map<String, Object>> wrappedDocList = new ArrayList<>();

        if (docObj instanceof List) {
            wrappedDocList = (List<Map<String, Object>>) docObj;
        } else if (docObj instanceof Map) {
            wrappedDocList.add((Map<String, Object>) docObj);
        }

        String lowerKeyword = keyword == null ? "" : keyword.toLowerCase();

        List<ExternalBookSearchDTO> result = new ArrayList<>();

        for (Map<String, Object> wrappedDoc : wrappedDocList) {
            Map<String, Object> doc = (Map<String, Object>) wrappedDoc.get("doc");

            if (doc == null) continue;

            String title = String.valueOf(doc.get("bookname"));

            if (!lowerKeyword.isBlank() && !title.toLowerCase().contains(lowerKeyword)) {
                continue;
            }

            ExternalBookSearchDTO dto = ExternalBookSearchDTO.builder()
                    .title((String) doc.get("bookname"))
                    .author((String) doc.get("authors"))
                    .publisher((String) doc.get("publisher"))
                    .publicationYear((String) doc.get("publication_year"))
                    .isbn(normalizeIsbn((String) doc.get("isbn13")))
                    .imageUrl((String) doc.get("bookImageURL"))
                    .category((String) doc.get("class_nm"))
                    .loanCount(parseInteger(doc.get("loan_count")))
                    .source("DATA4LIBRARY")
                    .build();

            result.add(dto);
        }

        return result;
    }

    private Integer parseInteger(Object value) {
        if (value == null) return 0;

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private String normalizeIsbn(String isbn) {
        if (isbn == null) return null;

        String onlyNumber = isbn.replaceAll("[^0-9Xx]", "");

        if (onlyNumber.length() >= 13) {
            return onlyNumber.substring(onlyNumber.length() - 13);
        }

        return onlyNumber;
    }
}