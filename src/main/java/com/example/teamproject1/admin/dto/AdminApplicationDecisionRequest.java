package com.example.teamproject1.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminApplicationDecisionRequest(
        @NotNull
        Long requesterUserId,

        @NotBlank
        String decision,

        String adminComment
) {
}
