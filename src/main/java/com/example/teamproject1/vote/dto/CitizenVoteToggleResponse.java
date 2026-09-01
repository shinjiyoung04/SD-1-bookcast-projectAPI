package com.example.teamproject1.vote.dto;

public record CitizenVoteToggleResponse(
        Long applicationId,
        boolean votedByMe,
        long voteCount,
        long recentVoteCount7d,
        String message
) {
}
