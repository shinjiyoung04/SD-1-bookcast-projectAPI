package com.example.teamproject1.libraryanalytics.controller;

import com.example.teamproject1.libraryanalytics.dto.ManagedLibraryAnalyticsResponse;
import com.example.teamproject1.libraryanalytics.dto.NationalGenreSnapshotRefreshResponse;
import com.example.teamproject1.libraryanalytics.service.AdminLibraryAnalyticsService;
import com.example.teamproject1.libraryanalytics.service.NationalGenreSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminLibraryAnalyticsController {

    private final AdminLibraryAnalyticsService
            adminLibraryAnalyticsService;

    private final NationalGenreSnapshotService
            nationalGenreSnapshotService;

    /**
     * 일반 도서관 관리자 담당 도서관 운영 분석
     *
     * GET /api/admin/library-analytics
     * ?requesterUserId=9
     * &refresh=false
     */
    @GetMapping("/library-analytics")
    public ResponseEntity<
            ManagedLibraryAnalyticsResponse
            > getManagedLibraryAnalytics(
            @RequestParam Long requesterUserId,
            @RequestParam(
                    defaultValue = "false"
            )
            boolean refresh
    ) {
        return ResponseEntity.ok(
                adminLibraryAnalyticsService
                        .getManagedLibraryAnalytics(
                                requesterUserId,
                                refresh
                        )
        );
    }
    // 전국 참여 도서관 장르별 장서 스냅샷 생성
    @PostMapping(
            "/library-analytics/national-snapshot/refresh"
    )
    public ResponseEntity<
            NationalGenreSnapshotRefreshResponse
            > refreshNationalGenreSnapshot(
            @RequestParam Long requesterUserId,
            @RequestParam(
                    defaultValue = "0"
            )
            int maxLibraries
    ) {
        return ResponseEntity.ok(
                nationalGenreSnapshotService
                        .refreshSnapshot(
                                requesterUserId,
                                maxLibraries
                        )
        );
    }

}
