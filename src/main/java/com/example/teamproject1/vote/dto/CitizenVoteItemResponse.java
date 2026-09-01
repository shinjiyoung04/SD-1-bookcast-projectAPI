package com.example.teamproject1.vote.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CitizenVoteItemResponse(
        Long applicationId,
        String title,
        String author,
        String publisher,
        String isbn,
        LocalDate publishedDate,
        String libraryName,
        String libCode,
        String reason,
        String status,
        LocalDateTime createdAt,
        long voteCount,
        long recentVoteCount7d,
        boolean votedByMe,
        double popularityIndex
) {
}
