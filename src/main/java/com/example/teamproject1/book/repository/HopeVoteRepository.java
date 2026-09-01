package com.example.teamproject1.book.repository;

import com.example.teamproject1.book.entity.HopeVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HopeVoteRepository extends JpaRepository<HopeVote, Long> {

    long countByApplicationApplicationId(Long applicationId);

    boolean existsByApplicationApplicationIdAndUserUserId(Long applicationId, Long userId);
}