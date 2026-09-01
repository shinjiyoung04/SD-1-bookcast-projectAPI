package com.example.teamproject1.book.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExternalBookDetailResponse {

    private String title;
    private String author;
    private String publisher;
    private String publicationYear;
    private String isbn13;
    private String description;
    private String imageUrl;
    private String detailUrl;
    private String source;
}