package com.example.teamproject1.book.dto;

import com.example.teamproject1.book.entity.enums.ApplicationStatus;
import com.example.teamproject1.book.entity.enums.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminApplicationProcessResponse {
    private Long applicationId;
    private String title;
    private ApplicationStatus status;
    private Long adminId;
    private String adminComment;
    private LocalDateTime processedAt;
    private Boolean isOwned;
    private LoanStatus loanStatus;
    private Integer totalCount;
    private Integer availableCount;

    private String message;

}
