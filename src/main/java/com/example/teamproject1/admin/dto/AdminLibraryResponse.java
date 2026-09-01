package com.example.teamproject1.admin.dto;

public record AdminLibraryResponse(
        Long libraryId,
        String libraryName,
        String address,
        String phone
) {
}
