package com.example.teamproject1.user.dto;

import com.example.teamproject1.user.entity.User;
import com.example.teamproject1.user.entity.UserRole;
import com.example.teamproject1.user.entity.UserStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponseDTO {

    private Long userId;
    private String loginId;
    private String name;
    private String nickname;
    private String email;
    private String profileImageUrl;
    private UserRole role;
    private UserStatus status;

    // 관리자 소속 도서관 정보
    private Long managedLibraryId;
    private String managedLibraryCode;
    private String managedLibraryName;

    // 일반 회원가입 응답
    public static UserResponseDTO fromEntity(
            User user
    ) {
        return fromEntity(
                user,
                null,
                null,
                null
        );
    }

    // 로그인할 때 소속 도서관 정보를 반환
    public static UserResponseDTO fromEntity(
            User user,
            Long managedLibraryId,
            String managedLibraryCode,
            String managedLibraryName
    ) {
        return UserResponseDTO.builder()
                .userId(
                        user.getUserId()
                )
                .loginId(
                        user.getLoginId()
                )
                .name(
                        user.getName()
                )
                .nickname(
                        user.getNickname()
                )
                .email(
                        user.getEmail()
                )
                .profileImageUrl(
                        user.getProfileImageUrl()
                )
                .role(
                        user.getRole()
                )
                .status(
                        user.getStatus()
                )
                .managedLibraryId(
                        managedLibraryId
                )
                .managedLibraryCode(
                        managedLibraryCode
                )
                .managedLibraryName(
                        managedLibraryName
                )
                .build();
    }
}