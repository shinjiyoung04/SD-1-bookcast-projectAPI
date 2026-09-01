package com.example.teamproject1.book.dto;

public record BookClassificationResponse(
        String isbn13,
        String title,
        String classNo,
        String className,
        Long categoryId,
        String categoryName,
        Integer loanCount,
        String source
) {
}
