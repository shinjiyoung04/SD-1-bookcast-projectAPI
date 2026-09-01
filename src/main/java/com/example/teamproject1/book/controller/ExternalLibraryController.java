package com.example.teamproject1.book.controller;

import com.example.teamproject1.book.dto.ExternalLibraryResponse;
import com.example.teamproject1.book.service.Data4LibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/external/libraries")
public class ExternalLibraryController {

    private static final String DEFAULT_REGION = "31";
    private static final String DEFAULT_DTL_REGION = "31100";

    private final Data4LibraryService data4LibraryService;

    /**
     * 지역별 도서관 목록 조회
     *
     * 기본 지역:
     * - region=31: 경기도
     * - dtlRegion=31100: 고양시 전체
     */
    @GetMapping
    public ResponseEntity<List<ExternalLibraryResponse>> searchLibraries(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "50") Integer pageSize,
            @RequestParam(defaultValue = DEFAULT_REGION) String region,
            @RequestParam(required = false) String dtlRegion,
            @RequestParam(
                    name = "dtl_region",
                    required = false
            ) String snakeDtlRegion,
            @RequestParam(required = false) String libCode,
            @RequestParam(required = false) String libName
    ) {
        String effectiveRegion =
                region == null || region.isBlank()
                        ? DEFAULT_REGION
                        : region.trim();

        String effectiveDtlRegion;

        if (dtlRegion != null && !dtlRegion.isBlank()) {
            effectiveDtlRegion = dtlRegion.trim();
        } else if (
                snakeDtlRegion != null &&
                        !snakeDtlRegion.isBlank()
        ) {
            effectiveDtlRegion = snakeDtlRegion.trim();
        } else {
            effectiveDtlRegion = DEFAULT_DTL_REGION;
        }

        List<ExternalLibraryResponse> response =
                data4LibraryService.searchLibraries(
                        pageNo,
                        pageSize,
                        effectiveRegion,
                        effectiveDtlRegion,
                        libCode
                );

        return ResponseEntity.ok(response);
    }
}