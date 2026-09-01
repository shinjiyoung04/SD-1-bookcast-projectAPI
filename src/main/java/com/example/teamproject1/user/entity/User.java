package com.example.teamproject1.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="user_id")
    private Long userId;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 50)
    private String nickname;

    @Column(length = 100)
    private String email;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "managed_library_id")
    private Long managedLibraryId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(length = 20)
    private String provider;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Column(name = "managed_library_code", length = 20)
    private String managedLibraryCode;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.role == UserRole.USER) {
            this.role = UserRole.USER;
        }
        if (this.role == UserRole.ADMIN) {
            this.role = UserRole.ADMIN;
        }
        if (this.provider == null) {
            this.provider = "LOCAL";
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void promoteToAdmin(String libraryCode) {
        if (this.role == UserRole.MASTER_ADMIN) {
            throw new IllegalStateException(
                    "최고 관리자 계정의 권한은 변경할 수 없습니다."
            );
        }

        if (libraryCode == null || libraryCode.isBlank()) {
            throw new IllegalArgumentException(
                    "관리 도서관 코드가 없습니다."
            );
        }

        this.role = UserRole.ADMIN;
        this.managedLibraryCode = libraryCode.trim();

        this.managedLibraryId = null;
    }
}
