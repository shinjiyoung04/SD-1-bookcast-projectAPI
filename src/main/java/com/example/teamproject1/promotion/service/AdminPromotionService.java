package com.example.teamproject1.promotion.service;

import com.example.teamproject1.promotion.dto.PromotionDecisionDTO;
import com.example.teamproject1.promotion.dto.PromotionRequestCreateDTO;
import com.example.teamproject1.promotion.dto.PromotionRequestResponseDTO;
import com.example.teamproject1.promotion.entity.AdminPromotionRequest;
import com.example.teamproject1.promotion.entity.PromotionStatus;
import com.example.teamproject1.promotion.repository.AdminPromotionRequestRepository;
import com.example.teamproject1.user.entity.User;
import com.example.teamproject1.user.entity.UserRole;
import com.example.teamproject1.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminPromotionService {

    private final AdminPromotionRequestRepository requestRepository;
    private final UserRepository userRepository;

    public PromotionRequestResponseDTO create(
            PromotionRequestCreateDTO dto
    ) {
        User user = getUser(dto.getUserId());

        if (
                user.getRole() == UserRole.ADMIN ||
                        user.getRole() == UserRole.MASTER_ADMIN
        ) {
            throw new RuntimeException(
                    "이미 관리자 권한을 가진 계정입니다."
            );
        }

        boolean alreadyPending =
                requestRepository
                        .existsByUser_UserIdAndStatus(
                                user.getUserId(),
                                PromotionStatus.PENDING
                        );

        if (alreadyPending) {
            throw new RuntimeException(
                    "이미 처리 대기 중인 등업 신청이 있습니다."
            );
        }

        validateCreate(dto);

        AdminPromotionRequest request =
                AdminPromotionRequest.builder()
                        .user(user)
                        .libraryName(
                                dto.getLibraryName().trim()
                        )
                        .libraryCode(
                                dto.getLibraryCode().trim()
                        )
                        .department(
                                dto.getDepartment().trim()
                        )
                        .employeeNumber(
                                dto.getEmployeeNumber().trim()
                        )
                        .contact(
                                dto.getContact().trim()
                        )
                        .reason(
                                dto.getReason().trim()
                        )
                        .status(
                                PromotionStatus.PENDING
                        )
                        .build();

        AdminPromotionRequest saved =
                requestRepository.save(request);

        return PromotionRequestResponseDTO
                .fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PromotionRequestResponseDTO myLatest(
            Long userId
    ) {
        return requestRepository
                .findTopByUser_UserIdOrderByCreatedAtDesc(
                        userId
                )
                .map(
                        PromotionRequestResponseDTO::fromEntity
                )
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PromotionRequestResponseDTO> list(
            PromotionStatus status,
            Long masterUserId
    ) {
        requireMaster(masterUserId);

        List<AdminPromotionRequest> requests;

        if (status == null) {
            requests =
                    requestRepository
                            .findAllByOrderByCreatedAtDesc();
        } else {
            requests =
                    requestRepository
                            .findByStatusOrderByCreatedAtAsc(
                                    status
                            );
        }

        return requests.stream()
                .map(
                        PromotionRequestResponseDTO::fromEntity
                )
                .toList();
    }

    public PromotionRequestResponseDTO approve(
            Long requestId,
            PromotionDecisionDTO dto
    ) {
        User master =
                requireMaster(dto.getMasterUserId());

        AdminPromotionRequest request =
                getPendingRequest(requestId);

        User targetUser = request.getUser();

        targetUser.setRole(UserRole.ADMIN);

        request.setStatus(
                PromotionStatus.APPROVED
        );

        request.setProcessedBy(master);

        request.setMasterComment(
                trimToNull(dto.getComment())
        );

        request.setProcessedAt(
                LocalDateTime.now()
        );

        return PromotionRequestResponseDTO
                .fromEntity(request);
    }

    public PromotionRequestResponseDTO reject(
            Long requestId,
            PromotionDecisionDTO dto
    ) {
        User master =
                requireMaster(dto.getMasterUserId());

        AdminPromotionRequest request =
                getPendingRequest(requestId);

        request.setStatus(
                PromotionStatus.REJECTED
        );

        request.setProcessedBy(master);

        request.setMasterComment(
                trimToNull(dto.getComment())
        );

        request.setProcessedAt(
                LocalDateTime.now()
        );

        return PromotionRequestResponseDTO
                .fromEntity(request);
    }

    public PromotionRequestResponseDTO cancel(
            Long requestId,
            Long userId
    ) {
        AdminPromotionRequest request =
                getPendingRequest(requestId);

        if (
                !request.getUser()
                        .getUserId()
                        .equals(userId)
        ) {
            throw new RuntimeException(
                    "본인의 신청만 취소할 수 있습니다."
            );
        }

        request.setStatus(
                PromotionStatus.CANCELED
        );

        request.setProcessedAt(
                LocalDateTime.now()
        );

        return PromotionRequestResponseDTO
                .fromEntity(request);
    }

    private User requireMaster(
            Long masterUserId
    ) {
        User master =
                getUser(masterUserId);

        if (
                master.getRole() !=
                        UserRole.MASTER_ADMIN
        ) {
            throw new RuntimeException(
                    "마스터 관리자 권한이 필요합니다."
            );
        }

        return master;
    }

    private User getUser(Long userId) {
        if (userId == null) {
            throw new RuntimeException(
                    "사용자 정보가 필요합니다."
            );
        }

        return userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new RuntimeException(
                                "사용자를 찾을 수 없습니다."
                        )
                );
    }

    private AdminPromotionRequest getPendingRequest(
            Long requestId
    ) {
        AdminPromotionRequest request =
                requestRepository
                        .findById(requestId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "등업 신청을 찾을 수 없습니다."
                                )
                        );

        if (
                request.getStatus() !=
                        PromotionStatus.PENDING
        ) {
            throw new RuntimeException(
                    "이미 처리된 신청입니다."
            );
        }

        return request;
    }

    private void validateCreate(
            PromotionRequestCreateDTO dto
    ) {
        if (
                isBlank(dto.getLibraryName()) ||
                        isBlank(dto.getLibraryCode()) ||
                        isBlank(dto.getDepartment()) ||
                        isBlank(dto.getEmployeeNumber()) ||
                        isBlank(dto.getContact()) ||
                        isBlank(dto.getReason())
        ) {
            throw new RuntimeException(
                    "모든 등업 신청 항목을 입력해주세요."
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null ||
                value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return isBlank(value)
                ? null
                : value.trim();
    }
}