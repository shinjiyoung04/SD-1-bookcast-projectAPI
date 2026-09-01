package com.example.teamproject1.memberaccount.dto;

public record MemberLibraryResponse(
        Long libraryId,
        String libraryName,
        String address,
        String phone
) {
}
