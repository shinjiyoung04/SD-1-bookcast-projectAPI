package com.example.teamproject1.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminMemberRoleUpdateRequest(
        @NotNull
        Long requesterUserId,

        @NotBlank
        String role,

        Long managedLibraryId
) {
}
