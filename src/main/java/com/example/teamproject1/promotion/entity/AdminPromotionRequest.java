package com.example.teamproject1.promotion.entity;

import com.example.teamproject1.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_promotion_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminPromotionRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "library_name", nullable = false, length = 100)
    private String libraryName;

    @Column(name = "library_code", nullable = false, length = 30)
    private String libraryCode;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "employee_number", nullable = false, length = 50)
    private String employeeNumber;

    @Column(name = "contact", nullable = false, length = 30)
    private String contact;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PromotionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "master_comment", columnDefinition = "TEXT")
    private String masterComment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = PromotionStatus.PENDING;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
