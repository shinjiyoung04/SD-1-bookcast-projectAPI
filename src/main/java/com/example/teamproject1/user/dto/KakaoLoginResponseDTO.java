package com.example.teamproject1.user.dto;

import com.example.teamproject1.user.entity.User;
import com.example.teamproject1.user.entity.UserRole;
import com.example.teamproject1.user.entity.UserStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KakaoLoginResponseDTO {

    private Long userId;
    private String loginId;
    private String name;
    private String nickname;
    private String email;
    private String profileImageUrl;
    private UserRole role;
    private UserStatus status;
    private String provider;
    private boolean newUser;

    public static KakaoLoginResponseDTO fromEntity(User user, boolean newUser) {
        return KakaoLoginResponseDTO.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .name(user.getName())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .provider(user.getProvider())
                .newUser(newUser)
                .build();
    }
}