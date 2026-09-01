package com.example.teamproject1.promotion.entity;

import com.example.teamproject1.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "promotion_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "library_name", nullable = false, length = 150)
    private String libraryName;

    @Column(name = "library_code", nullable = false, length = 30)
    private String libraryCode;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(name = "employee_number", nullable = false, length = 100)
    private String employeeNumber;

    @Column(nullable = false, length = 50)
    private String contact;

    @Lob
    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_admin_id")
    private User masterAdmin;

    @Lob
    @Column(name = "master_comment")
    private String masterComment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PromotionRequest(
            User user,
            String libraryName,
            String libraryCode,
            String department,
            String employeeNumber,
            String contact,
            String reason
    ) {
        this.user = user;
        this.libraryName = libraryName;
        this.libraryCode = libraryCode;
        this.department = department;
        this.employeeNumber = employeeNumber;
        this.contact = contact;
        this.reason = reason;
        this.status = PromotionStatus.PENDING;
    }

    public void approve(
            User masterAdmin,
            String masterComment
    ) {
        validatePending();

        this.status = PromotionStatus.APPROVED;
        this.masterAdmin = masterAdmin;
        this.masterComment = normalizeComment(masterComment);
        this.processedAt = LocalDateTime.now();
    }

    public void reject(
            User masterAdmin,
            String masterComment
    ) {
        validatePending();

        this.status = PromotionStatus.REJECTED;
        this.masterAdmin = masterAdmin;
        this.masterComment = normalizeComment(masterComment);
        this.processedAt = LocalDateTime.now();
    }

    public void cancel(Long requestingUserId) {
        validatePending();

        if (!user.getUserId().equals(requestingUserId)) {
            throw new IllegalArgumentException(
                    "본인의 등업 신청만 취소할 수 있습니다."
            );
        }

        this.status = PromotionStatus.CANCELED;
        this.processedAt = LocalDateTime.now();
    }

    private void validatePending() {
        if (status != PromotionStatus.PENDING) {
            throw new IllegalStateException(
                    "이미 처리된 등업 신청입니다."
            );
        }
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }

        return comment.trim();
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = PromotionStatus.PENDING;
        }
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}