package com.example.teamproject1.vote.detail.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class CitizenVoteAiDtos {

    private CitizenVoteAiDtos() {
    }

    public record PredictionRequest(
            @JsonProperty("application_id")
            Long applicationId,

            @JsonProperty("book_id")
            Long bookId,

            String title,
            String author,
            String publisher,
            Double kdc,

            @JsonProperty("library_name")
            String libraryName,

            @JsonProperty("vote_count")
            Long voteCount,

            @JsonProperty("recent_vote_count_7d")
            Long recentVoteCount7d
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PredictionResponse(
            boolean success,

            @JsonProperty("application_id")
            Long applicationId,

            @JsonProperty("book_id")
            Long bookId,

            @JsonProperty("approval_probability")
            Double approvalProbability,

            @JsonProperty("popularity_score")
            Double popularityScore,

            @JsonProperty("vote_adjustment")
            Double voteAdjustment,

            @JsonProperty("final_score")
            Double finalScore,

            @JsonProperty("model_version")
            String modelVersion,

            @JsonProperty("base_priority_score")
            Double basePriorityScore,

            @JsonProperty("p1_genre_balance")
            Double p1GenreBalance,

            @JsonProperty("p2_local_affinity")
            Double p2LocalAffinity,

            @JsonProperty("p3_ai_capacity")
            Double p3AiCapacity,

            @JsonProperty("kdc_main")
            String kdcMain,

            @JsonProperty("applied_library")
            String appliedLibrary,

            @JsonProperty("ai_comment")
            String aiComment,

            @JsonProperty("probability_notice")
            String probabilityNotice
    ) {
    }

    public record SavedPrediction(
            Long predictionId,
            Long applicationId,
            Long bookId,
            Double approvalProbability,
            Double popularityScore,
            Double voteAdjustment,
            Double finalScore,
            String modelVersion
    ) {
    }
}
