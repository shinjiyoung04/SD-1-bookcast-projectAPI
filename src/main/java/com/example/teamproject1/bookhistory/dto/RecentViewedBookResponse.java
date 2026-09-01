package com.example.teamproject1.bookhistory.dto;

import java.time.LocalDateTime;

public record RecentViewedBookResponse(
        Long bookId,
        String isbn,
        String title,
        String author,
        String publisher,
        String thumbnailUrl,
        LocalDateTime viewedAt
) {
}
