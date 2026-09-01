package com.example.teamproject1.memberaccount.dto;

public record MemberWithdrawResponse(
        Long userId,
        String status,
        String message
) {
}
