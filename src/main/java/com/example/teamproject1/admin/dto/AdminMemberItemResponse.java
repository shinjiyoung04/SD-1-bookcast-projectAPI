package com.example.teamproject1.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminMemberItemResponse(
        Long userId,
        String loginId,
        String name,
        String nickname,
        String email,
        String profileImageUrl,
        String address,
        LocalDate birthDate,
        String gender,
        String role,
        String status,
        String provider,
        Long managedLibraryId,
        String managedLibraryCode,
        String managedLibraryName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
