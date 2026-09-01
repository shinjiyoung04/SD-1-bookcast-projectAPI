package com.example.teamproject1.admin.dto;

public record AdminSelfResponse(
        Long userId,
        String loginId,
        String name,
        String nickname,
        String email,
        String profileImageUrl,
        String role,
        String status,
        Long managedLibraryId,
        String managedLibraryCode,
        String managedLibraryName,
        long pendingApplicationCount
) {
}
