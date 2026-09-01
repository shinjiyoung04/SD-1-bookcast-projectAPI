package com.example.teamproject1.promotion.repository;

import com.example.teamproject1.promotion.entity.PromotionRequest;
import com.example.teamproject1.promotion.entity.PromotionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromotionRequestRepository
        extends JpaRepository<PromotionRequest, Long> {

    // 특정 사용자의 특정 상태 신청 존재 여부 확인

    boolean existsByUser_UserIdAndStatus(
            Long userId,
            PromotionStatus status
    );

    // 사용자의 가장 최근 등업 신청 조회

    @EntityGraph(attributePaths = {
            "user",
            "masterAdmin"
    })
    Optional<PromotionRequest>
    findTopByUser_UserIdOrderByCreatedAtDesc(
            Long userId
    );


    @EntityGraph(attributePaths = {
            "user",
            "masterAdmin"
    })
    Optional<PromotionRequest>
    findTopByUser_UserIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            PromotionStatus status
    );

    // 전체 등업 신청 목록을 최신순으로 조회

    @EntityGraph(attributePaths = {
            "user",
            "masterAdmin"
    })
    List<PromotionRequest>
    findAllByOrderByCreatedAtDesc();

    // 특정 상태의 등업 신청 목록을 최신순으로 조회

    @EntityGraph(attributePaths = {
            "user",
            "masterAdmin"
    })
    List<PromotionRequest>
    findAllByStatusOrderByCreatedAtDesc(
            PromotionStatus status
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM PromotionRequest request
            JOIN FETCH request.user
            LEFT JOIN FETCH request.masterAdmin
            WHERE request.requestId = :requestId
            """)
    Optional<PromotionRequest> findByIdForUpdate(
            @Param("requestId")
            Long requestId
    );
}