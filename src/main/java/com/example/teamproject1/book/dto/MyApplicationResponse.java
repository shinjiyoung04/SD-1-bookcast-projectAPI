package com.example.teamproject1.book.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyApplicationResponse {

    private Long applicationId;

    private String bookTitle;

    private String author;

    private String publisher;

    private String isbn13;

    private String libraryName;

    private String status;

    private String reason;

    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
}