package com.example.teamproject1.book.dto;

import com.example.teamproject1.book.entity.enums.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class CreateApplicationResponse {

    private Long applicationId;
    private String title;
    private String author;
    private ApplicationStatus status;
    private BigDecimal approvalProbability;
    private BigDecimal popularityScore;
    private BigDecimal finalScore;
    private String message;
}
