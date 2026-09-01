package com.example.teamproject1.book.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class ApproveApplicationRequest {
    @NotNull
    private Long adminId;

    private String adminComment;
}
