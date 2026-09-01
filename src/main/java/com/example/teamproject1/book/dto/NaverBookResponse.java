package com.example.teamproject1.book.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NaverBookResponse {

    private String title;
    private String author;
    private String publisher;

    // yyyy
    private String publicationYear;

    // yyyy-MM-dd
    private String publishedDate;

    private String isbn13;

    private String classNo;
    private String className;

    private String imageUrl;
    private String detailUrl;
    private Integer loanCount;
    private String description;

    // NAVER, ALADIN, NAVER+ALADIN
    private String source;

    // 알라딘에서 제공하는 선택 정보
    private Integer priceSales;
    private Integer priceStandard;
    private Integer salesPoint;
}
