package com.example.teamproject1.book.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_predictions")
@Getter
@Setter
@NoArgsConstructor
public class AiPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prediction_id")
    private Long predictionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private HopeApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name = "approval_probability", nullable = false, precision = 5, scale = 2)
    private BigDecimal approvalProbability;

    @Column(name = "popularity_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal popularityScore;

    @Column(name = "vote_adjustment", nullable = false, precision = 5, scale = 2)
    private BigDecimal voteAdjustment;

    @Column(name = "final_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal finalScore;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}