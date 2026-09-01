package com.example.teamproject1.admin.dto;

public record AdminDashboardResponse(
        long pendingCount,
        long todayCount,
        long activeVoteCount,
        double averageApprovalProbability
) {
}
