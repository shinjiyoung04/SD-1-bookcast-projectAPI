package com.example.teamproject1.promotion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PromotionCreateRequest(

        @NotNull
        Long userId,

        @NotBlank
        String libraryName,

        @NotBlank
        String libraryCode,

        @NotBlank
        String department,

        @NotBlank
        String employeeNumber,

        @NotBlank
        String contact,

        @NotBlank
        String reason
) {
}