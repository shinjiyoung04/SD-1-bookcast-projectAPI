package com.example.teamproject1.book.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookImportRequestDTO {

    private String title;
    private String author;
    private String publisher;
    private String category;
    private String isbn;
    private String description;
    private String imageUrl;
    private Integer loanCount;
}
