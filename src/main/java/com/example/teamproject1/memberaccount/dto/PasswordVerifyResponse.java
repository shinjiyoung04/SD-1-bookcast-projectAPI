package com.example.teamproject1.memberaccount.dto;

import java.time.LocalDateTime;

public record PasswordVerifyResponse(
        String verificationToken,
        LocalDateTime expiresAt
) {
}
