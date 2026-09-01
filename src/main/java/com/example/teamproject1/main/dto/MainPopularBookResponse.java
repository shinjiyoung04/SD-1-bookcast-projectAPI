package com.example.teamproject1.main.dto;

public record MainPopularBookResponse(
        Integer rank,
        String title,
        String author,
        String publisher,
        String publicationYear,
        String isbn13,
        String className,
        String imageUrl,
        Integer loanCount,
        String dataStartDate,
        String dataEndDate
) {
}
