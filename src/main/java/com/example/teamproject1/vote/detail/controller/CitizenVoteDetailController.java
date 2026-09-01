package com.example.teamproject1.vote.detail.controller;

import com.example.teamproject1.vote.detail.dto.CitizenVoteDetailDtos;
import com.example.teamproject1.vote.detail.service.CitizenVoteAiPredictionService;
import com.example.teamproject1.vote.detail.service.CitizenVoteDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/citizen-votes")
public class CitizenVoteDetailController {

    private final CitizenVoteDetailService
            citizenVoteDetailService;

    private final CitizenVoteAiPredictionService
            citizenVoteAiPredictionService;

    @GetMapping("/{applicationId}/detail")
    public ResponseEntity<CitizenVoteDetailDtos.DetailResponse>
    getDetail(
            @PathVariable(name = "applicationId")
            Long applicationId,

            @RequestParam(name = "requesterUserId")
            Long requesterUserId
    ) {
        return ResponseEntity.ok(
                citizenVoteDetailService
                        .getDetail(
                                requesterUserId,
                                applicationId
                        )
        );
    }

    @PostMapping("/{applicationId}/predict")
    public ResponseEntity<CitizenVoteDetailDtos.DetailResponse>
    predictApproval(
            @PathVariable(name = "applicationId")
            Long applicationId,

            @RequestParam(name = "requesterUserId")
            Long requesterUserId,

            @RequestParam(
                    name = "force",
                    defaultValue = "false"
            )
            boolean force
    ) {
        citizenVoteAiPredictionService
                .predictAndSave(
                        requesterUserId,
                        applicationId,
                        force
                );

        return ResponseEntity.ok(
                citizenVoteDetailService
                        .getDetail(
                                requesterUserId,
                                applicationId
                        )
        );
    }

    @PatchMapping("/{applicationId}/cancel")
    public ResponseEntity<CitizenVoteDetailDtos.DetailResponse>
    cancelApplication(
            @PathVariable(name = "applicationId")
            Long applicationId,

            @RequestParam(name = "requesterUserId")
            Long requesterUserId
    ) {
        return ResponseEntity.ok(
                citizenVoteDetailService
                        .cancelApplication(
                                requesterUserId,
                                applicationId
                        )
        );
    }
}
