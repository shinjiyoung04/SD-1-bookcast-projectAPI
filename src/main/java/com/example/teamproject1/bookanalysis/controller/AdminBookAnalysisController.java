package com.example.teamproject1.bookanalysis.controller;

import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse;
import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse.LoanTrendItemResponse;
import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse.PopularGroupItemResponse;
import com.example.teamproject1.bookanalysis.dto.PurchaseEvidenceResponse;
import com.example.teamproject1.bookanalysis.service.BookPurchaseEvidenceService;
import com.example.teamproject1.bookanalysis.service.Data4LibraryUsageAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/books")
public class AdminBookAnalysisController {

    private final Data4LibraryUsageAnalysisService
            usageAnalysisService;

    private final BookPurchaseEvidenceService
            purchaseEvidenceService;

    /**
     * 정보나루 도서별 이용분석 전체 조회
     *
     * GET /api/admin/books/{isbn}/usage-analysis
     */
    @GetMapping("/{isbn}/usage-analysis")
    public ResponseEntity<BookUsageAnalysisResponse>
    getUsageAnalysis(
            @PathVariable String isbn
    ) {
        return ResponseEntity.ok(
                usageAnalysisService
                        .getUsageAnalysis(
                                isbn
                        )
        );
    }

    // 사서용 도서 구매 판단 근거 조회
    @GetMapping("/{isbn}/purchase-evidence")
    public ResponseEntity<PurchaseEvidenceResponse>
    getPurchaseEvidence(
            @PathVariable String isbn,
            @RequestParam Long requesterUserId
    ) {
        return ResponseEntity.ok(
                purchaseEvidenceService
                        .getPurchaseEvidence(
                                requesterUserId,
                                isbn
                        )
        );
    }

    @GetMapping("/{isbn}/loan-trend")
    public ResponseEntity<List<LoanTrendItemResponse>>
    getLoanTrend(
            @PathVariable String isbn
    ) {
        return ResponseEntity.ok(
                usageAnalysisService
                        .getUsageAnalysis(
                                isbn
                        )
                        .loanTrend()
        );
    }

    @GetMapping("/{isbn}/popular-groups")
    public ResponseEntity<List<PopularGroupItemResponse>>
    getPopularGroups(
            @PathVariable String isbn
    ) {
        return ResponseEntity.ok(
                usageAnalysisService
                        .getUsageAnalysis(
                                isbn
                        )
                        .popularGroups()
        );
    }
}
