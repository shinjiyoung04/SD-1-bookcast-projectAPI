package com.example.teamproject1.vote.dto;

import jakarta.validation.constraints.NotNull;

public record CitizenVoteToggleRequest(@NotNull Long userId) {
}
