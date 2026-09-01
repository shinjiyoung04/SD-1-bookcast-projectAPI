package com.example.teamproject1.application.duplicate.dto;

public record ApplicationDuplicateCheckResponse(
        boolean duplicate,
        boolean ownApplication,
        Long applicationId,
        String title,
        Long libraryId,
        String libraryCode,
        String libraryName,
        String status,
        long voteCount,
        String message,
        String redirectUrl
) {

    public static ApplicationDuplicateCheckResponse notDuplicate() {
        return new ApplicationDuplicateCheckResponse(
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                0L,
                null,
                null
        );
    }
}
