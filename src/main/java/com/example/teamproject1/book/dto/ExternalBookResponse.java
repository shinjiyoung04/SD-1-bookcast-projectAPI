package com.example.teamproject1.book.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExternalBookResponse {

    private String bookname;
    private String authors;
    private String publisher;
    private String publicationYear;
    private String isbn13;
    private String classNo;
    private String className;
    private String bookImageUrl;
    private String bookDetailUrl;
    private Integer loanCount;

    public String getTitle() {
        return bookname;
    }

    public String getAuthor() {
        return authors;
    }

    public String getImageUrl() {
        return bookImageUrl;
    }

    public String getDetailUrl() {
        return bookDetailUrl;
    }
}