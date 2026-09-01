package com.example.teamproject1.application.duplicate.controller;

import com.example.teamproject1.application.duplicate.dto.ApplicationDuplicateCheckResponse;
import com.example.teamproject1.application.duplicate.service.ApplicationDuplicateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/applications")
public class ApplicationDuplicateController {

    private final ApplicationDuplicateService applicationDuplicateService;

    // 희망도서 신청 중복 체크
    @GetMapping("/duplicate-check")
    public ResponseEntity<ApplicationDuplicateCheckResponse> checkDuplicate(
            @RequestParam(name = "userId")
            Long userId,

            @RequestParam(name = "isbn")
            String isbn,

            @RequestParam(name = "libraryId", required = false)
            Long libraryId,

            @RequestParam(name = "libCode", required = false)
            String libCode
    ) {
        ApplicationDuplicateCheckResponse response =
                applicationDuplicateService.checkDuplicate(
                        userId,
                        isbn,
                        libraryId,
                        libCode
                );

        return ResponseEntity.ok(response);
    }
}
