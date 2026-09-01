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
@RequestMapping("/api/external/books")
public class ExternalBookLibraryController {

    private static final String DEFAULT_REGION = "31";
    private static final String DEFAULT_DTL_REGION = "31100";

    private final Data4LibraryService data4LibraryService;

    /**
     * ISBN 기준 소장 도서관 조회
     *
     * 기본 지역:
     * - region=31: 경기도
     * - dtlRegion=31100: 고양시 전체
    
     */
    @GetMapping("/libraries")
    public ResponseEntity<List<ExternalLibraryResponse>> searchLibrariesByBook(
            @RequestParam String isbn,
            @RequestParam(defaultValue = DEFAULT_REGION) String region,
            @RequestParam(defaultValue = DEFAULT_DTL_REGION) String dtlRegion,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        String effectiveRegion =
                region == null || region.isBlank()
                        ? DEFAULT_REGION
                        : region.trim();

        String effectiveDtlRegion;

        if ("ALL".equalsIgnoreCase(effectiveRegion)) {
            effectiveDtlRegion = null;
        } else if (DEFAULT_REGION.equals(effectiveRegion)) {
            effectiveDtlRegion =
                    dtlRegion == null || dtlRegion.isBlank()
                            ? DEFAULT_DTL_REGION
                            : dtlRegion.trim();
        } else {
            effectiveDtlRegion =
                    dtlRegion == null || dtlRegion.isBlank()
                            ? null
                            : dtlRegion.trim();
        }

        List<ExternalLibraryResponse> response =
                data4LibraryService.searchLibrariesByBook(
                        isbn,
                        effectiveRegion,
                        effectiveDtlRegion,
                        pageNo,
                        pageSize
                );

        return ResponseEntity.ok(response);
    }
}
