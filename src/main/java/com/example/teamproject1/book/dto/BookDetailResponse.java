package com.example.teamproject1.book.dto;

import com.example.teamproject1.book.entity.enums.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class BookDetailResponse {

    private Long bookId;
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private LocalDate publishedDate;

    private Long categoryId;
    private String categoryName;

    private String description;
    private String thumbnailUrl;
    private Integer viewCount;
    private BigDecimal averageRating;

    private Long libraryId;
    private String libraryName;
    private String libCode;

    private Boolean isOwned;
    private Boolean isLoanAvailable;
    private LoanStatus loanStatus;
    private Boolean canApplyHope;
    private String message;
}