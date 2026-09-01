package com.example.teamproject1.memberaccount.dto;

import java.time.LocalDate;

public record MemberProfileResponse(
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
        String managedLibraryName
) {
}
