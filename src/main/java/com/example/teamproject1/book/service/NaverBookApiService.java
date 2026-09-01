package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.ExternalBookSearchDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NaverBookApiService {

    @Value("${external.naver.client-id}")
    private String clientId;

    @Value("${external.naver.client-secret}")
    private String clientSecret;

    public List<ExternalBookSearchDTO> search(String keyword, int display, int start) {
        RestTemplate restTemplate = new RestTemplate();

        String url = UriComponentsBuilder
                .fromHttpUrl("https://openapi.naver.com/v1/search/book.json")
                .queryParam("query", keyword)
                .queryParam("display", display)
                .queryParam("start", start)
                .queryParam("sort", "sim")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class
        );

        Map<String, Object> body = response.getBody();

        if (body == null || body.get("items") == null) {
            return List.of();
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

        List<ExternalBookSearchDTO> result = new ArrayList<>();

        for (Map<String, Object> item : items) {
            ExternalBookSearchDTO dto = ExternalBookSearchDTO.builder()
                    .title(clean((String) item.get("title")))
                    .author(clean((String) item.get("author")))
                    .publisher(clean((String) item.get("publisher")))
                    .publicationYear((String) item.get("pubdate"))
                    .isbn(normalizeIsbn((String) item.get("isbn")))
                    .description(clean((String) item.get("description")))
                    .imageUrl((String) item.get("image"))
                    .naverLink((String) item.get("link"))
                    .source("NAVER")
                    .build();

            result.add(dto);
        }

        return result;
    }

    private String normalizeIsbn(String isbn) {
        if (isbn == null) return null;

        String onlyNumber = isbn.replaceAll("[^0-9Xx]", "");

        if (onlyNumber.length() >= 13) {
            return onlyNumber.substring(onlyNumber.length() - 13);
        }

        return onlyNumber;
    }

    private String clean(String value) {
        if (value == null) return null;

        return value
                .replaceAll("<[^>]*>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim();
    }
}