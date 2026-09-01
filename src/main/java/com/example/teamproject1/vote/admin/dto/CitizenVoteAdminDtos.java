package com.example.teamproject1.vote.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CitizenVoteAdminDtos {

    private CitizenVoteAdminDtos() {
    }

    public record LibraryOptionResponse(
            Long libraryId,
            String libraryName
    ) {
    }

    public record ItemResponse(
            Long applicationId,
            String title,
            String author,
            String publisher,
            String isbn,
            LocalDate publishedDate,
            Long libraryId,
            String libraryName,
            Long applicantUserId,
            String applicantName,
            String applicantLoginId,
            String status,
            LocalDateTime createdAt,
            long voteCount,
            long recentVoteCount7d,
            double popularityIndex
    ) {
    }

    public record PageResponse(
            List<ItemResponse> content,
            int page,
            int pageSize,
            long totalElements,
            int totalPages,
            long totalVotes,
            long recentVotes7d,
            String requesterRole,
            Long managedLibraryId,
            String managedLibraryName,
            String scopeLabel
    ) {
    }

    public record DetailResponse(
            Long applicationId,
            String title,
            String author,
            String publisher,
            String isbn,
            LocalDate publishedDate,
            Long libraryId,
            String libraryName,
            Long applicantUserId,
            String applicantName,
            String applicantLoginId,
            String applicantEmail,
            String reason,
            String status,
            LocalDateTime createdAt,
            long voteCount,
            long recentVoteCount7d,
            double popularityIndex
    ) {
    }
}
