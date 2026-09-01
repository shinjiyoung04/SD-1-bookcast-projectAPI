package com.example.teamproject1.promotion.dto;

import com.example.teamproject1.promotion.entity.AdminPromotionRequest;
import com.example.teamproject1.promotion.entity.PromotionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter @Builder
public class PromotionRequestResponseDTO {
    private Long requestId;
    private Long userId;
    private String loginId;
    private String name;
    private String nickname;
    private String email;
    private String libraryName;
    private String libraryCode;
    private String department;
    private String employeeNumber;
    private String contact;
    private String reason;
    private PromotionStatus status;
    private Long processedByUserId;
    private String processedByName;
    private String masterComment;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public static PromotionRequestResponseDTO fromEntity(AdminPromotionRequest request) {
        return PromotionRequestResponseDTO.builder()
                .requestId(request.getRequestId())
                .userId(request.getUser().getUserId())
                .loginId(request.getUser().getLoginId())
                .name(request.getUser().getName())
                .nickname(request.getUser().getNickname())
                .email(request.getUser().getEmail())
                .libraryName(request.getLibraryName())
                .libraryCode(request.getLibraryCode())
                .department(request.getDepartment())
                .employeeNumber(request.getEmployeeNumber())
                .contact(request.getContact())
                .reason(request.getReason())
                .status(request.getStatus())
                .processedByUserId(request.getProcessedBy() == null ? null : request.getProcessedBy().getUserId())
                .processedByName(request.getProcessedBy() == null ? null : request.getProcessedBy().getName())
                .masterComment(request.getMasterComment())
                .createdAt(request.getCreatedAt())
                .processedAt(request.getProcessedAt())
                .build();
    }
}
