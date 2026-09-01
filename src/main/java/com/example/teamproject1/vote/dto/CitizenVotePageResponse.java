package com.example.teamproject1.vote.dto;

import java.util.List;

public record CitizenVotePageResponse(
        List<CitizenVoteItemResponse> content,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {
}
