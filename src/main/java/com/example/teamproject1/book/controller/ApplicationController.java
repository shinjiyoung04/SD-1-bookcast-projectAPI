package com.example.teamproject1.book.controller;

import com.example.teamproject1.book.dto.CreateApplicationRequest;
import com.example.teamproject1.book.dto.CreateApplicationResponse;
import com.example.teamproject1.book.dto.MyApplicationResponse;
import com.example.teamproject1.book.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    // 희망도서 신청
    @PostMapping
    public ResponseEntity<CreateApplicationResponse> createApplication(
            @Valid @RequestBody CreateApplicationRequest request
    ) {
        CreateApplicationResponse response =
                applicationService.createApplication(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 사용자별 희망도서 신청 목록
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<MyApplicationResponse>> getMyApplications(
            @PathVariable Long userId
    ) {
        List<MyApplicationResponse> response =
                applicationService.getMyApplications(userId);

        return ResponseEntity.ok(response);
    }
}