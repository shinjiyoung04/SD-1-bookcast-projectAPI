package com.example.teamproject1.application.dto;

public record ApplicationLibraryResolveResponse(
        Long libraryId,
        String libraryCode,
        String libraryName,
        String address,
        String phone
) {
}