package com.example.teamproject1.book.dto;

import com.example.teamproject1.book.entity.enums.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserApplicationListResponse {

    private Long applicationId;
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String libraryName;
    private ApplicationStatus status;
    private String reason;
    private String adminComment;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    
    public UserApplicationListResponse(Long applicationId, String title, String author,
                                       ApplicationStatus status, String reason, String adminComment,
                                       LocalDateTime createdAt, LocalDateTime processedAt) {
        this.applicationId = applicationId;
        this.title = title;
        this.author = author;
        this.status = status;
        this.reason = reason;
        this.adminComment = adminComment;
        this.createdAt = createdAt;
        this.processedAt = processedAt;


        this.isbn = null;
        this.publisher = null;
        this.libraryName = null;
    }
}