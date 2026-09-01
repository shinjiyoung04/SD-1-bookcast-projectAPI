package com.example.teamproject1.promotion.dto;

import jakarta.validation.constraints.NotNull;

public record PromotionDecisionRequest(

        @NotNull
        Long masterAdminId,

        String comment
) {
}