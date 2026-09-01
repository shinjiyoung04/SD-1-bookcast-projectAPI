package com.example.teamproject1.book.dto;

import com.example.teamproject1.book.entity.enums.ApplicationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AdminApplicationListResponse {

    private Long applicationId;
    private String title;
    private String author;
    private ApplicationStatus status;

    private String applicantLoginId;
    private String applicantNickname;

    private String libraryName;

    private BigDecimal approvalProbability;
    private BigDecimal popularityScore;
    private BigDecimal finalScore;

    private String adminComment;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public AdminApplicationListResponse(
            Long applicationId,
            String title,
            String author,
            ApplicationStatus status,
            String applicantLoginId,
            String applicantNickname,
            String libraryName,
            BigDecimal approvalProbability,
            BigDecimal popularityScore,
            BigDecimal finalScore,
            String adminComment,
            LocalDateTime createdAt,
            LocalDateTime processedAt
    ) {
        this.applicationId = applicationId;
        this.title = title;
        this.author = author;
        this.status = status;
        this.applicantLoginId = applicantLoginId;
        this.applicantNickname = applicantNickname;
        this.libraryName = libraryName;
        this.approvalProbability = approvalProbability;
        this.popularityScore = popularityScore;
        this.finalScore = finalScore;
        this.adminComment = adminComment;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getApplicantLoginId() {
        return applicantLoginId;
    }

    public String getApplicantNickname() {
        return applicantNickname;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public BigDecimal getApprovalProbability() {
        return approvalProbability;
    }

    public BigDecimal getPopularityScore() {
        return popularityScore;
    }

    public BigDecimal getFinalScore() {
        return finalScore;
    }

    public String getAdminComment() {
        return adminComment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}