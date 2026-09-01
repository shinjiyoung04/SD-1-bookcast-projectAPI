package com.example.teamproject1.promotion.repository;

import com.example.teamproject1.promotion.entity.AdminPromotionRequest;
import com.example.teamproject1.promotion.entity.PromotionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminPromotionRequestRepository extends JpaRepository<AdminPromotionRequest, Long> {
    boolean existsByUser_UserIdAndStatus(Long userId, PromotionStatus status);
    Optional<AdminPromotionRequest> findTopByUser_UserIdOrderByCreatedAtDesc(Long userId);
    List<AdminPromotionRequest> findAllByOrderByCreatedAtDesc();
    List<AdminPromotionRequest> findByStatusOrderByCreatedAtAsc(PromotionStatus status);
}
