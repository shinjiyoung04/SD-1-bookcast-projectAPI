package com.example.teamproject1.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectApplicationRequest {

    @NotNull
    private Long adminId;

    @NotBlank
    private String adminComment;
}
