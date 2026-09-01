package com.example.teamproject1.book.repository;

import com.example.teamproject1.book.dto.AdminApplicationListResponse;
import com.example.teamproject1.book.dto.UserApplicationListResponse;
import com.example.teamproject1.book.entity.HopeApplication;
import com.example.teamproject1.book.entity.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HopeApplicationRepository extends JpaRepository<HopeApplication, Long> {

    @Query("""
            SELECT new com.example.teamproject1.book.dto.AdminApplicationListResponse(
                ha.applicationId,
                ha.title,
                ha.author,
                ha.status,
                u.loginId,
                u.nickname,
                l.libraryName,
                ap.approvalProbability,
                ap.popularityScore,
                ap.finalScore,
                ha.adminComment,
                ha.createdAt,
                ha.processedAt
            )
            FROM HopeApplication ha
            JOIN ha.user u
            JOIN ha.library l
            LEFT JOIN AiPrediction ap ON ap.application = ha
            ORDER BY ha.applicationId DESC
            """)
    List<AdminApplicationListResponse> findAdminApplicationList();

    @Query("""
            SELECT new com.example.teamproject1.book.dto.UserApplicationListResponse(
                ha.applicationId,
                ha.title,
                ha.author,
                ha.status,
                ha.reason,
                ha.adminComment,
                ha.createdAt,
                ha.processedAt
            )
            FROM HopeApplication ha
            WHERE ha.user.userId = :userId
            ORDER BY ha.applicationId DESC
            """)
    List<UserApplicationListResponse> findUserApplicationList(Long userId);

    @Query("""
            SELECT ha
            FROM HopeApplication ha
            LEFT JOIN FETCH ha.book b
            LEFT JOIN FETCH b.category c
            JOIN FETCH ha.library l
            WHERE ha.status = :status
            ORDER BY ha.applicationId DESC
            """)
    List<HopeApplication> findVoteApplicationsByStatus(ApplicationStatus status);
}