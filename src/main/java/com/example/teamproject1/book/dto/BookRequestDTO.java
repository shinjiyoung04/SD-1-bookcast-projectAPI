package com.example.teamproject1.book.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BookRequestDTO {

    private String title;
    private String author;
    private String publisher;
    private String category;
    private String isbn;
    private String description;
    private String imageUrl;
    private String thumbnailUrl;
    private String publishedDate;
}