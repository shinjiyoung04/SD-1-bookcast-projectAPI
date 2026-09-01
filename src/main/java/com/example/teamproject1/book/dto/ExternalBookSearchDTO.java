package com.example.teamproject1.book.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalBookSearchDTO {

    private String title;
    private String author;
    private String publisher;
    private String publicationYear;
    private String isbn;
    private String description;


    private String imageUrl;

    private String category;
    private Integer loanCount;

    private String naverLink;
    private String nlDetailLink;

    private boolean saved;

    private String source;
}