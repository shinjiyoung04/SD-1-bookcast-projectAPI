package com.example.teamproject1.bookhistory.dto;

public record BookViewLogRequest(
        String isbn,
        String title,
        String author,
        String publisher,
        String thumbnailUrl
) {
}
