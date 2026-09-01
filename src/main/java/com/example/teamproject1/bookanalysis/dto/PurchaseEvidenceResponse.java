package com.example.teamproject1.bookanalysis.dto;

import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse.KeywordItemResponse;
import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse.LoanTrendItemResponse;
import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse.PopularGroupItemResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseEvidenceResponse(

        String isbn13,

        LibraryInfoResponse library,

        BookInfoResponse book,

        HoldingEvidenceResponse holding,

        LocalCitizenDemandResponse localCitizenDemand,

        LibraryCategoryDemandResponse libraryCategoryDemand,

        NationalDemandResponse nationalDemand,

        FreshnessEvidenceResponse freshness,

        List<String> evidenceSummary
) {

    /**
     * 로그인 관리자의 담당 도서관
     */
    public record LibraryInfoResponse(
            Long libraryId,
            String libCode,
            String libraryName
    ) {
    }

    /**
     * 구매 검토 대상 도서 기본정보
     */
    public record BookInfoResponse(
            String title,
            String author,
            String publisher,
            String publicationYear,
            String classNo,
            String className,
            String imageUrl
    ) {
    }

    /**
     * 담당 도서관의 소장 여부
     *
     * available:
     * 정보나루 API가 정상적으로 소장 여부를 반환했는지 여부
     *
     * owned:
     * 담당 도서관이 현재 도서를 소장하고 있는지 여부
     */
    public record HoldingEvidenceResponse(
            Boolean available,
            Boolean owned,
            String hasBook,
            String message
    ) {
    }

    /**
     * 프로젝트 내부 시민 수요
     *
     * pendingApplicationCount:
     * 현재 담당 도서관에 접수된 미처리 희망도서 신청 수
     *
     * activeVoteCount:
     * 해당 신청들에 대한 활성 시민투표 수
     */
    public record LocalCitizenDemandResponse(
            Boolean available,
            Long pendingApplicationCount,
            Long activeVoteCount,
            LocalDateTime latestApplicationAt,
            String message
    ) {
    }

    // 담당 도서관의 동일 KDC 분야 수요

    public record LibraryCategoryDemandResponse(
            Boolean available,
            String classNo,
            String className,
            LocalDate startDate,
            LocalDate endDate,
            Integer returnedBookCount,
            Integer sameCategoryBookCount,
            Integer sameCategoryLoanCount,
            String demandLevel,
            List<CategoryPopularBookResponse> topBooks,
            String message
    ) {
    }

    // 담당 도서관 동일 분야의 인기대출 도서

    public record CategoryPopularBookResponse(
            Integer ranking,
            String title,
            String author,
            String publisher,
            String isbn13,
            String classNo,
            String className,
            Integer loanCount
    ) {
    }

    // 전국 공공도서관 기준 해당 도서의 이용 수요

    public record NationalDemandResponse(
            Boolean available,
            Integer totalLoanCount,
            String trendStatus,
            List<LoanTrendItemResponse> loanTrend,
            List<PopularGroupItemResponse> popularGroups,
            List<KeywordItemResponse> keywords,
            String message
    ) {
    }

    // 도서 출판연도 기준 최신성

    public record FreshnessEvidenceResponse(
            String publicationYear,
            Integer yearsSincePublication,
            String freshnessLevel
    ) {
    }
}
