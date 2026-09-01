package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.ExternalBookSearchDTO;
import com.example.teamproject1.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExternalBookSearchService {

    private final NaverBookApiService naverBookApiService;
    private final Data4LibraryApiService data4LibraryApiService;
    private final NationalLibraryApiService nationalLibraryApiService;
    private final BookRepository bookRepository;

    public List<ExternalBookSearchDTO> search(String keyword, int page, int size) {
        int naverStart = page * size + 1;

        List<ExternalBookSearchDTO> naverBooks = new ArrayList<>();
        List<ExternalBookSearchDTO> data4LibraryBooks = new ArrayList<>();
        List<ExternalBookSearchDTO> nationalLibraryBooks = new ArrayList<>();

        try {
            naverBooks = naverBookApiService.search(keyword, size, naverStart);
            System.out.println("네이버 검색 결과 수 = " + naverBooks.size());
        } catch (Exception e) {
            System.out.println("네이버 API 호출 실패");
            e.printStackTrace();
        }

        try {
            data4LibraryBooks = data4LibraryApiService.searchPopular(keyword, 1, 100);
            System.out.println("정보나루 검색 결과 수 = " + data4LibraryBooks.size());
        } catch (Exception e) {
            System.out.println("정보나루 API 호출 실패");
            e.printStackTrace();
        }

        try {
            nationalLibraryBooks = nationalLibraryApiService.search(keyword, page + 1, size);
            System.out.println("국립중앙도서관 검색 결과 수 = " + nationalLibraryBooks.size());
        } catch (Exception e) {
            System.out.println("국립중앙도서관 API 호출 실패");
            e.printStackTrace();
        }

        Map<String, ExternalBookSearchDTO> mergedMap = new LinkedHashMap<>();

        mergeBooks(mergedMap, naverBooks);
        mergeBooks(mergedMap, data4LibraryBooks);
        mergeBooks(mergedMap, nationalLibraryBooks);

        List<ExternalBookSearchDTO> result = new ArrayList<>(mergedMap.values());

        for (ExternalBookSearchDTO book : result) {
            if (book.getIsbn() != null && !book.getIsbn().isBlank()) {
                book.setSaved(bookRepository.existsByIsbn(book.getIsbn()));
            }
        }

        return result;
    }

    private void mergeBooks(
            Map<String, ExternalBookSearchDTO> mergedMap,
            List<ExternalBookSearchDTO> books
    ) {
        for (ExternalBookSearchDTO book : books) {
            String key = book.getIsbn();

            if (key == null || key.isBlank()) {
                key = safe(book.getTitle()) + "_" + safe(book.getAuthor());
            }

            if (!mergedMap.containsKey(key)) {
                mergedMap.put(key, book);
                continue;
            }

            ExternalBookSearchDTO existing = mergedMap.get(key);

            if (isBlank(existing.getDescription())) {
                existing.setDescription(book.getDescription());
            }

            if (isBlank(existing.getImageUrl())) {
                existing.setImageUrl(book.getImageUrl());
            }

            if (isBlank(existing.getCategory())) {
                existing.setCategory(book.getCategory());
            }

            if (existing.getLoanCount() == null || existing.getLoanCount() == 0) {
                existing.setLoanCount(book.getLoanCount());
            }

            if (isBlank(existing.getNaverLink())) {
                existing.setNaverLink(book.getNaverLink());
            }

            if (isBlank(existing.getNlDetailLink())) {
                existing.setNlDetailLink(book.getNlDetailLink());
            }

            existing.setSource("MERGED");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}