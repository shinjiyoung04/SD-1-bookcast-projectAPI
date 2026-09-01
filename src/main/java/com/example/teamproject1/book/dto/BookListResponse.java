package com.example.teamproject1.book.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class BookListResponse {

    private Long bookId;
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private LocalDate publishedDate;

    private Long categoryId;
    private String categoryName;

    private String thumbnailUrl;
    private Integer viewCount;
    private BigDecimal averageRating;
}