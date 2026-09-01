package com.example.teamproject1.main.airecommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class MainAiRecommendationDtos {

    private MainAiRecommendationDtos() {
    }

    public record Request(
            List<Candidate> candidates,
            Integer limit,
            Boolean force
    ) {
    }

    public record Candidate(
            String isbn13,
            String title,
            String author,
            String publisher,
            String classNo,
            String categoryName,
            String imageUrl,
            Long loanCount,
            Integer rank,
            String dataStartDate,
            String dataEndDate
    ) {
    }

    public record AiRequest(
            String title,
            String author,
            String publisher,
            Double kdc,

            @JsonProperty("library_name")
            String libraryName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiResponse(
            boolean success,

            @JsonProperty("kdc_main")
            String kdcMain,

            @JsonProperty("p1_genre_balance")
            Double p1GenreBalance,

            @JsonProperty("p2_local_affinity")
            Double p2LocalAffinity,

            @JsonProperty("p3_ai_capacity")
            Double p3AiCapacity,

            @JsonProperty("base_priority_score")
            Double basePriorityScore,

            @JsonProperty("applied_library")
            String appliedLibrary,

            @JsonProperty("ai_comment")
            String aiComment
    ) {
    }

    public record RecommendedBook(
            Integer aiRank,
            String isbn13,
            String title,
            String author,
            String publisher,
            String imageUrl,
            Long loanCount,
            Integer rank,
            String dataStartDate,
            String dataEndDate,
            Double recommendationScore,
            Double popularityScore,
            Double genreBalanceScore,
            Double localAffinityScore,
            String recommendationLevel,
            String resolvedKdc,
            String kdcMain,
            String modelVersion,
            String aiComment,
            boolean cached
    ) {
    }
}
