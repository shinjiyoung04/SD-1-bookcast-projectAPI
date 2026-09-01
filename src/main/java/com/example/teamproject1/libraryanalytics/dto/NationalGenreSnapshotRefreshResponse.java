package com.example.teamproject1.libraryanalytics.dto;

import java.time.LocalDate;
import java.util.Map;

public record NationalGenreSnapshotRefreshResponse(
        LocalDate snapshotDate,
        int requestedLibraryCount,
        int completedLibraryCount,
        int failedLibraryCount,
        long totalHoldingCount,
        Map<String, Long> genreHoldings
) {
}
