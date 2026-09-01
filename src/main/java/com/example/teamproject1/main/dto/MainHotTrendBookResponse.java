package com.example.teamproject1.main.dto;

public record MainHotTrendBookResponse(
        String date,
        Integer no,
        Integer difference,
        Integer baseWeekRank,
        Integer pastWeekRank,
        Integer ranking,
        String title,
        String author,
        String publisher,
        String publicationYear,
        String isbn13,
        String className,
        String imageUrl
) {
}
