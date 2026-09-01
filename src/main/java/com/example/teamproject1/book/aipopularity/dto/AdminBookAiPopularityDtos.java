package com.example.teamproject1.book.aipopularity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class AdminBookAiPopularityDtos {

    private AdminBookAiPopularityDtos() {
    }

    public record Request(
            Long requesterUserId,
            String title,
            String author,
            String publisher,
            String classNo,
            String categoryName,
            Boolean force
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

    public record Response(
            String isbn13,
            String status,
            Double popularityScore,
            String popularityLevel,
            Double basePriorityScore,
            Double genreBalanceScore,
            Double localAffinityScore,
            String resolvedKdc,
            String kdcMain,
            String appliedLibrary,
            String modelVersion,
            String aiComment,
            boolean cached
    ) {
    }
}
