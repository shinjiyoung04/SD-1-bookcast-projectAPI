package com.example.teamproject1.book.controller;

import com.example.teamproject1.book.dto.VoteApplicationResponse;
import com.example.teamproject1.book.dto.VoteCreateResponse;
import com.example.teamproject1.book.service.VoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;

    @GetMapping("/votes/applications")
    public List<VoteApplicationResponse> getVoteApplications(
            @RequestParam(required = false) Long userId
    ) {
        return voteService.getVoteApplications(userId);
    }

    @PostMapping("/applications/{applicationId}/votes")
    public VoteCreateResponse createVote(
            @PathVariable Long applicationId,
            @RequestParam Long userId
    ) {
        return voteService.createVote(applicationId, userId);
    }
}