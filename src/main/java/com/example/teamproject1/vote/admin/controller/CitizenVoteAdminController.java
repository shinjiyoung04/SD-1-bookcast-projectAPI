package com.example.teamproject1.vote.admin.controller;

import com.example.teamproject1.vote.admin.dto.CitizenVoteAdminDtos;
import com.example.teamproject1.vote.admin.service.CitizenVoteAdminService;
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
@RequestMapping("/api/citizen-votes/admin")
public class CitizenVoteAdminController {

    private final CitizenVoteAdminService citizenVoteAdminService;

    @GetMapping
    public ResponseEntity<CitizenVoteAdminDtos.PageResponse> getApplications(
            @RequestParam Long requesterUserId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "POPULAR") String sort,
            @RequestParam(required = false) Long libraryId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "9") Integer pageSize
    ) {
        return ResponseEntity.ok(
                citizenVoteAdminService.getApplications(
                        requesterUserId,
                        keyword,
                        status,
                        sort,
                        libraryId,
                        page,
                        pageSize
                )
        );
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<CitizenVoteAdminDtos.DetailResponse> getApplicationDetail(
            @PathVariable Long applicationId,
            @RequestParam Long requesterUserId
    ) {
        return ResponseEntity.ok(
                citizenVoteAdminService.getApplicationDetail(
                        requesterUserId,
                        applicationId
                )
        );
    }

    @GetMapping("/libraries")
    public ResponseEntity<List<CitizenVoteAdminDtos.LibraryOptionResponse>> getLibraries(
            @RequestParam Long requesterUserId
    ) {
        return ResponseEntity.ok(
                citizenVoteAdminService.getLibraryOptions(requesterUserId)
        );
    }
}
