package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.ExternalBookSearchDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NationalLibraryApiService {

    @Value("${external.nl.key}")
    private String nlKey;

    public List<ExternalBookSearchDTO> search(String keyword, int pageNum, int pageSize) {
        RestTemplate restTemplate = new RestTemplate();

        String url = UriComponentsBuilder
                .fromHttpUrl("https://www.nl.go.kr/NL/search/openApi/search.do")
                .queryParam("key", nlKey)
                .queryParam("apiType", "json")
                .queryParam("srchTarget", "total")
                .queryParam("kwd", keyword)
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize)
                .queryParam("category", "도서")
                .build()
                .toUriString();

        Map<String, Object> body = restTemplate.getForObject(url, Map.class);

        if (body == null) {
            return List.of();
        }

        Object resultObj = body.get("result");

        if (!(resultObj instanceof List)) {
            return List.of();
        }

        List<Map<String, Object>> resultList = (List<Map<String, Object>>) resultObj;

        return resultList.stream()
                .map(item -> ExternalBookSearchDTO.builder()
                        .title((String) item.get("title_info"))
                        .author((String) item.get("author_info"))
                        .publisher((String) item.get("pub_info"))
                        .publicationYear((String) item.get("pub_year_info"))
                        .isbn(normalizeIsbn((String) item.get("isbn")))
                        .category((String) item.get("kdc_name_1s"))
                        .nlDetailLink((String) item.get("detail_link"))
                        .source("NATIONAL_LIBRARY")
                        .build())
                .toList();
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