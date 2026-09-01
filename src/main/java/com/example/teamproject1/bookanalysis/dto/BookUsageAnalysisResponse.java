package com.example.teamproject1.bookanalysis.dto;

import java.util.List;

public record BookUsageAnalysisResponse(
        String isbn13,
        Integer totalLoanCount,
        List<LoanTrendItemResponse> loanTrend,
        List<PopularGroupItemResponse> popularGroups,
        List<KeywordItemResponse> keywords
) {
    public record LoanTrendItemResponse(
            String loanMonth,
            Integer loanCount,
            Integer ranking
    ) {
    }

    public record PopularGroupItemResponse(
            String age,
            String gender,
            Integer loanCount,
            Integer ranking
    ) {
    }

    public record KeywordItemResponse(
            String word,
            Double weight
    ) {
    }
}
