package com.example.teamproject1.vote.detail.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CitizenVoteDetailDtos {

    private CitizenVoteDetailDtos() {
    }

    public record PredictionResponse(
            String status,
            Long predictionId,
            Double approvalProbability,
            Double popularityScore,
            Double voteAdjustment,
            Double finalScore,
            String modelVersion,
            LocalDateTime predictedAt
    ) {
    }

    public record DetailResponse(
            Long applicationId,
            Long applicantUserId,
            String applicantName,
            boolean isOwner,
            String requesterRole,

            String title,
            String author,
            String publisher,
            String isbn,
            String thumbnailUrl,
            LocalDate publishedDate,

            Long libraryId,
            String libraryName,

            String reason,
            String status,
            String adminComment,
            LocalDateTime createdAt,
            LocalDateTime processedAt,

            long voteCount,
            long recentVoteCount7d,
            boolean votedByMe,
            boolean canVote,
            boolean canCancel,
            double popularityIndex,

            PredictionResponse prediction
    ) {
    }
}
