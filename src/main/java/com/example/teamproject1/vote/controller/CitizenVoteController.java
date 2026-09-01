package com.example.teamproject1.vote.controller;

import com.example.teamproject1.vote.dto.CitizenVotePageResponse;
import com.example.teamproject1.vote.dto.CitizenVoteToggleRequest;
import com.example.teamproject1.vote.dto.CitizenVoteToggleResponse;
import com.example.teamproject1.vote.service.CitizenVoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/citizen-votes")
public class CitizenVoteController {

    private final CitizenVoteService citizenVoteService;

    @GetMapping
    public ResponseEntity<CitizenVotePageResponse> getApplications(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "POPULAR") String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer pageSize
    ) {
        return ResponseEntity.ok(
                citizenVoteService.getPublicApplications(
                        userId,
                        keyword,
                        status,
                        sort,
                        page,
                        pageSize
                )
        );
    }

    @PostMapping("/{applicationId}/toggle")
    public ResponseEntity<CitizenVoteToggleResponse> toggleVote(
            @PathVariable Long applicationId,
            @Valid @RequestBody CitizenVoteToggleRequest request
    ) {
        return ResponseEntity.ok(
                citizenVoteService.toggleVote(
                        applicationId,
                        request.userId()
                )
        );
    }
}
