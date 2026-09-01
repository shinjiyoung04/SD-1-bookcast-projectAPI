package com.example.teamproject1.libraryinfo.dto;

import java.time.LocalDateTime;

public record ManagedLibraryInfoResponse(
        Long libraryId,
        String libraryCode,
        String libraryName,
        String address,
        String tel,
        String fax,
        String homepage,
        String closed,
        String operatingTime,
        long bookCount,
        String latitude,
        String longitude,
        boolean dataAvailable,
        String message,
        LocalDateTime generatedAt
) {
}
