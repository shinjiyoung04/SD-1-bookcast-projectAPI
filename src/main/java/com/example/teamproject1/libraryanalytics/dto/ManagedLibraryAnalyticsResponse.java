package com.example.teamproject1.libraryanalytics.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ManagedLibraryAnalyticsResponse(
        Long libraryId,
        String libraryCode,
        String libraryName,
        long libraryHoldingCount,
        long cumulativeLoanCount,
        boolean collectionComplete,
        String comparisonMode,
        String comparisonDescription,
        double loanActivityIndex,
        double returnActivityIndex,
        String topLoanDay,
        String topLoanHour,
        List<GenreComparison> genreComparison,
        List<UsagePoint> dayTrend,
        List<UsagePoint> hourTrend,
        VisitorSummary visitorSummary,
        LocalDateTime generatedAt
) {

    public record GenreComparison(
            String kdcCode,
            String kdcName,
            long nationalMetricValue,
            long libraryHoldingCount,
            double nationalShare,
            double libraryShare,
            double differencePoints
    ) {
    }

    public record UsagePoint(
            String label,
            double loan,
            double returnCount
    ) {
    }

    public record VisitorSummary(
            boolean available,
            long todayVisitors,
            long visitors30Days,
            long activeBorrowers30Days,
            long programParticipants30Days,
            LocalDate dataStartDate,
            LocalDate dataEndDate
    ) {
        public static VisitorSummary unavailable() {
            return new VisitorSummary(
                    false,
                    0L,
                    0L,
                    0L,
                    0L,
                    null,
                    null
            );
        }
    }
}
