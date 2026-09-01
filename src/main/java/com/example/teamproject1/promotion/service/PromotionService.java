package com.example.teamproject1.promotion.service;

import com.example.teamproject1.common.service.ManagedLibrarySyncService;
import com.example.teamproject1.common.service.ManagedLibrarySyncService.ManagedLibrary;
import com.example.teamproject1.promotion.dto.PromotionCreateRequest;
import com.example.teamproject1.promotion.dto.PromotionResponse;
import com.example.teamproject1.promotion.entity.PromotionRequest;
import com.example.teamproject1.promotion.entity.PromotionStatus;
import com.example.teamproject1.promotion.repository.PromotionRequestRepository;
import com.example.teamproject1.user.entity.User;
import com.example.teamproject1.user.entity.UserRole;
import com.example.teamproject1.user.entity.UserStatus;
import com.example.teamproject1.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {

    private final PromotionRequestRepository
            promotionRequestRepository;

    private final UserRepository userRepository;

    private final ManagedLibrarySyncService
            managedLibrarySyncService;

    @Transactional
    public PromotionResponse create(
            PromotionCreateRequest request
    ) {
        User user = getActiveUser(
                request.userId()
        );

        if (user.getRole() != UserRole.USER) {
            throw new IllegalStateException(
                    "일반 사용자만 관리자 등업을 신청할 수 있습니다."
            );
        }

        boolean hasPendingRequest =
                promotionRequestRepository
                        .existsByUser_UserIdAndStatus(
                                user.getUserId(),
                                PromotionStatus.PENDING
                        );

        if (hasPendingRequest) {
            throw new IllegalStateException(
                    "현재 승인 대기 중인 등업 신청이 있습니다."
            );
        }

        PromotionRequest entity =
                new PromotionRequest(
                        user,
                        normalizeRequired(
                                request.libraryName(),
                                "도서관명"
                        ),
                        normalizeRequired(
                                request.libraryCode(),
                                "도서관 코드"
                        ),
                        normalizeRequired(
                                request.department(),
                                "부서"
                        ),
                        normalizeRequired(
                                request.employeeNumber(),
                                "사원번호"
                        ),
                        normalizeRequired(
                                request.contact(),
                                "연락처"
                        ),
                        normalizeRequired(
                                request.reason(),
                                "신청 사유"
                        )
                );

        PromotionRequest saved =
                promotionRequestRepository.save(
                        entity
                );

        return toResponse(saved);
    }

    public PromotionResponse getMyLatest(
            Long userId
    ) {
        User user = getActiveUser(userId);

        if (user.getRole() == UserRole.ADMIN
                || user.getRole()
                == UserRole.MASTER_ADMIN) {
            return promotionRequestRepository
                    .findTopByUser_UserIdOrderByCreatedAtDesc(
                            userId
                    )
                    .map(this::toResponse)
                    .orElse(null);
        }

        return promotionRequestRepository
                .findTopByUser_UserIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        PromotionStatus.PENDING
                )
                .map(this::toResponse)
                .orElse(null);
    }

    public List<PromotionResponse> getRequests(
            Long masterAdminId,
            PromotionStatus status
    ) {
        validateMasterAdmin(masterAdminId);

        List<PromotionRequest> requests =
                status == null
                        ? promotionRequestRepository
                        .findAllByOrderByCreatedAtDesc()
                        : promotionRequestRepository
                        .findAllByStatusOrderByCreatedAtDesc(
                                status
                        );

        return requests.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PromotionResponse approve(
            Long requestId,
            Long masterAdminId,
            String comment
    ) {
        User masterAdmin =
                validateMasterAdmin(
                        masterAdminId
                );

        PromotionRequest promotionRequest =
                getRequestForUpdate(
                        requestId
                );

        if (promotionRequest.getStatus()
                != PromotionStatus.PENDING) {
            throw new IllegalStateException(
                    "이미 처리된 등업 신청입니다."
            );
        }

        User applicant =
                promotionRequest.getUser();

        if (applicant.getStatus()
                != UserStatus.ACTIVE) {
            throw new IllegalStateException(
                    "활성 상태가 아닌 계정은 관리자로 승인할 수 없습니다."
            );
        }

        if (applicant.getRole()
                != UserRole.USER) {
            throw new IllegalStateException(
                    "일반 사용자 계정만 관리자로 승인할 수 있습니다."
            );
        }

        String libraryCode =
                normalizeRequired(
                        promotionRequest
                                .getLibraryCode(),
                        "도서관 코드"
                );

        String libraryName =
                normalizeRequired(
                        promotionRequest
                                .getLibraryName(),
                        "도서관명"
                );

        // 신청 당시 선택한 정보나루 도서관을 내부 libraries 테이블에 등록하거나 갱신
        ManagedLibrary managedLibrary =
                managedLibrarySyncService
                        .syncLibrary(
                                libraryCode,
                                libraryName,
                                null,
                                null
                        );

        applicant.promoteToAdmin(
                libraryCode
        );

        userRepository.saveAndFlush(
                applicant
        );

        // 내부 library_id와 정보나루 library_code를 사용자에게 지정

        managedLibrarySyncService
                .assignLibraryToUser(
                        applicant.getUserId(),
                        managedLibrary
                );

        promotionRequest.approve(
                masterAdmin,
                normalizeDecisionComment(
                        comment
                )
        );

        promotionRequestRepository.save(
                promotionRequest
        );

        return toResponse(
                promotionRequest
        );
    }

    @Transactional
    public PromotionResponse reject(
            Long requestId,
            Long masterAdminId,
            String comment
    ) {
        if (comment == null
                || comment.isBlank()) {
            throw new IllegalArgumentException(
                    "반려 사유를 입력해주세요."
            );
        }

        User masterAdmin =
                validateMasterAdmin(
                        masterAdminId
                );

        PromotionRequest promotionRequest =
                getRequestForUpdate(
                        requestId
                );

        if (promotionRequest.getStatus()
                != PromotionStatus.PENDING) {
            throw new IllegalStateException(
                    "이미 처리된 등업 신청입니다."
            );
        }

        promotionRequest.reject(
                masterAdmin,
                comment.trim()
        );

        promotionRequestRepository.save(
                promotionRequest
        );

        return toResponse(
                promotionRequest
        );
    }

    @Transactional
    public PromotionResponse cancel(
            Long requestId,
            Long userId
    ) {
        getActiveUser(userId);

        PromotionRequest promotionRequest =
                getRequestForUpdate(
                        requestId
                );

        if (promotionRequest.getStatus()
                != PromotionStatus.PENDING) {
            throw new IllegalStateException(
                    "승인 대기 중인 신청만 취소할 수 있습니다."
            );
        }

        promotionRequest.cancel(
                userId
        );

        promotionRequestRepository.save(
                promotionRequest
        );

        return toResponse(
                promotionRequest
        );
    }

    private User validateMasterAdmin(
            Long masterAdminId
    ) {
        User user = getActiveUser(
                masterAdminId
        );

        if (user.getRole()
                != UserRole.MASTER_ADMIN) {
            throw new IllegalStateException(
                    "최고 관리자만 등업 신청을 처리할 수 있습니다."
            );
        }

        return user;
    }

    private User getActiveUser(
            Long userId
    ) {
        User user = getUser(userId);

        if (user.getStatus()
                != UserStatus.ACTIVE) {
            throw new IllegalStateException(
                    "활성 상태의 사용자만 이용할 수 있습니다."
            );
        }

        return user;
    }

    private User getUser(
            Long userId
    ) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "사용자 번호가 필요합니다."
            );
        }

        return userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다. userId="
                                        + userId
                        )
                );
    }

    private PromotionRequest getRequestForUpdate(
            Long requestId
    ) {
        if (requestId == null) {
            throw new IllegalArgumentException(
                    "등업 신청 번호가 필요합니다."
            );
        }

        return promotionRequestRepository
                .findByIdForUpdate(
                        requestId
                )
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "등업 신청을 찾을 수 없습니다. requestId="
                                        + requestId
                        )
                );
    }

    private String normalizeRequired(
            String value,
            String fieldName
    ) {
        if (value == null
                || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName
                            + "을(를) 입력해주세요."
            );
        }

        return value.trim();
    }

    private String normalizeDecisionComment(
            String comment
    ) {
        return comment == null
                || comment.isBlank()
                ? "관리자 등업 승인"
                : comment.trim();
    }

    private PromotionResponse toResponse(
            PromotionRequest request
    ) {
        User applicant =
                request.getUser();

        User masterAdmin =
                request.getMasterAdmin();

        return new PromotionResponse(
                request.getRequestId(),

                applicant.getUserId(),
                applicant.getLoginId(),
                applicant.getName(),
                applicant.getEmail(),

                request.getLibraryName(),
                request.getLibraryCode(),
                request.getDepartment(),
                request.getEmployeeNumber(),
                request.getContact(),
                request.getReason(),

                request.getStatus(),

                masterAdmin != null
                        ? masterAdmin.getUserId()
                        : null,

                masterAdmin != null
                        ? masterAdmin.getName()
                        : null,

                request.getMasterComment(),

                request.getCreatedAt(),
                request.getProcessedAt(),
                request.getUpdatedAt()
        );
    }
}