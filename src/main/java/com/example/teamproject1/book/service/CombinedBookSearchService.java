package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.NaverBookResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CombinedBookSearchService {

    private final NaverBookSearchService naverBookSearchService;
    private final AladinBookSearchService aladinBookSearchService;

    public List<NaverBookResponse> searchBooks(
            String provider,
            String keyword,
            String title,
            String author,
            String isbn13,
            String publisher,
            Integer pageNo,
            Integer pageSize
    ) {
        String normalizedProvider = normalizeProvider(provider);

        int safePageSize = pageSize == null
                ? 10
                : Math.max(1, pageSize);

        if ("NAVER".equals(normalizedProvider)) {
            return naverBookSearchService.searchBooks(
                    keyword,
                    title,
                    author,
                    isbn13,
                    publisher,
                    pageNo,
                    safePageSize
            );
        }

        if ("ALADIN".equals(normalizedProvider)) {
            return aladinBookSearchService.searchBooks(
                    keyword,
                    title,
                    author,
                    isbn13,
                    publisher,
                    pageNo,
                    safePageSize
            );
        }

        List<NaverBookResponse> naverBooks = new ArrayList<>();
        List<NaverBookResponse> aladinBooks = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            naverBooks = naverBookSearchService.searchBooks(
                    keyword,
                    title,
                    author,
                    isbn13,
                    publisher,
                    pageNo,
                    safePageSize
            );
        } catch (Exception e) {
            log.error("[통합 도서 검색] 네이버 검색 실패", e);
            errors.add("네이버: " + e.getMessage());
        }

        try {
            aladinBooks = aladinBookSearchService.searchBooks(
                    keyword,
                    title,
                    author,
                    isbn13,
                    publisher,
                    pageNo,
                    safePageSize
            );
        } catch (Exception e) {
            log.error("[통합 도서 검색] 알라딘 검색 실패", e);
            errors.add("알라딘: " + e.getMessage());
        }

        if (naverBooks.isEmpty()
                && aladinBooks.isEmpty()
                && !errors.isEmpty()) {
            throw new RuntimeException(
                    "도서 검색 API를 모두 호출하지 못했습니다. "
                            + String.join(" / ", errors)
            );
        }

        List<NaverBookResponse> interleaved =
                interleave(naverBooks, aladinBooks);

        List<NaverBookResponse> merged =
                mergeDuplicateBooks(interleaved);

        if (merged.size() > safePageSize) {
            merged = new ArrayList<>(
                    merged.subList(0, safePageSize)
            );
        }

        log.info(
                "[통합 도서 검색 결과] naver={}, aladin={}, merged={}, returned={}",
                naverBooks.size(),
                aladinBooks.size(),
                mergeDuplicateBooks(interleaved).size(),
                merged.size()
        );

        return merged;
    }

    private List<NaverBookResponse> interleave(
            List<NaverBookResponse> naverBooks,
            List<NaverBookResponse> aladinBooks
    ) {
        List<NaverBookResponse> result = new ArrayList<>();

        int maxSize = Math.max(
                naverBooks.size(),
                aladinBooks.size()
        );

        for (int index = 0; index < maxSize; index++) {
            if (index < naverBooks.size()) {
                result.add(naverBooks.get(index));
            }

            if (index < aladinBooks.size()) {
                result.add(aladinBooks.get(index));
            }
        }

        return result;
    }

    private List<NaverBookResponse> mergeDuplicateBooks(
            List<NaverBookResponse> books
    ) {
        Map<String, NaverBookResponse> resultMap =
                new LinkedHashMap<>();

        for (NaverBookResponse book : books) {
            String key = createBookKey(book);

            NaverBookResponse existing = resultMap.get(key);

            if (existing == null) {
                resultMap.put(key, book);
                continue;
            }

            resultMap.put(
                    key,
                    mergeBook(existing, book)
            );
        }

        return new ArrayList<>(resultMap.values());
    }

    private String createBookKey(NaverBookResponse book) {
        String isbn = normalizeIsbn(book.getIsbn13());

        if (StringUtils.hasText(isbn)) {
            return "ISBN:" + isbn;
        }

        String title = normalizeKeyText(book.getTitle());
        String author = normalizeKeyText(book.getAuthor());

        return "TEXT:" + title + "|" + author;
    }

    private NaverBookResponse mergeBook(
            NaverBookResponse first,
            NaverBookResponse second
    ) {
        return first.toBuilder()
                .title(firstText(first.getTitle(), second.getTitle()))
                .author(firstText(first.getAuthor(), second.getAuthor()))
                .publisher(
                        firstText(
                                first.getPublisher(),
                                second.getPublisher()
                        )
                )
                .publicationYear(
                        firstText(
                                first.getPublicationYear(),
                                second.getPublicationYear()
                        )
                )
                .publishedDate(
                        firstText(
                                first.getPublishedDate(),
                                second.getPublishedDate()
                        )
                )
                .isbn13(
                        firstText(
                                first.getIsbn13(),
                                second.getIsbn13()
                        )
                )
                .classNo(
                        firstText(
                                first.getClassNo(),
                                second.getClassNo()
                        )
                )
                .className(
                        firstText(
                                second.getClassName(),
                                first.getClassName()
                        )
                )
                .imageUrl(
                        firstText(
                                first.getImageUrl(),
                                second.getImageUrl()
                        )
                )
                .detailUrl(
                        firstText(
                                first.getDetailUrl(),
                                second.getDetailUrl()
                        )
                )
                .loanCount(
                        firstNumber(
                                first.getLoanCount(),
                                second.getLoanCount()
                        )
                )
                .description(
                        longerText(
                                first.getDescription(),
                                second.getDescription()
                        )
                )
                .source(
                        mergeSource(
                                first.getSource(),
                                second.getSource()
                        )
                )
                .priceSales(
                        firstNumber(
                                first.getPriceSales(),
                                second.getPriceSales()
                        )
                )
                .priceStandard(
                        firstNumber(
                                first.getPriceStandard(),
                                second.getPriceStandard()
                        )
                )
                .salesPoint(
                        firstNumber(
                                first.getSalesPoint(),
                                second.getSalesPoint()
                        )
                )
                .build();
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "ALL";
        }

        String normalized = provider
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!List.of("ALL", "NAVER", "ALADIN")
                .contains(normalized)) {
            throw new IllegalArgumentException(
                    "provider는 ALL, NAVER, ALADIN 중 하나여야 합니다."
            );
        }

        return normalized;
    }

    private String normalizeIsbn(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.replaceAll("[^0-9Xx]", "");
    }

    private String normalizeKeyText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value
                .replaceAll("<[^>]*>", "")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }

        return StringUtils.hasText(second)
                ? second
                : null;
    }

    private String longerText(String first, String second) {
        if (!StringUtils.hasText(first)) {
            return second;
        }

        if (!StringUtils.hasText(second)) {
            return first;
        }

        return second.length() > first.length()
                ? second
                : first;
    }

    private Integer firstNumber(
            Integer first,
            Integer second
    ) {
        return first != null
                ? first
                : second;
    }

    private String mergeSource(
            String first,
            String second
    ) {
        if (!StringUtils.hasText(first)) {
            return second;
        }

        if (!StringUtils.hasText(second)) {
            return first;
        }

        if (first.equalsIgnoreCase(second)) {
            return first;
        }

        boolean hasNaver =
                first.toUpperCase(Locale.ROOT).contains("NAVER")
                        || second.toUpperCase(Locale.ROOT).contains("NAVER");

        boolean hasAladin =
                first.toUpperCase(Locale.ROOT).contains("ALADIN")
                        || second.toUpperCase(Locale.ROOT).contains("ALADIN");

        if (hasNaver && hasAladin) {
            return "NAVER+ALADIN";
        }

        return first + "+" + second;
    }
}
