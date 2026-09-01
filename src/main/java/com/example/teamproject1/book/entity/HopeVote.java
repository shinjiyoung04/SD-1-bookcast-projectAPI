package com.example.teamproject1.book.entity;

import com.example.teamproject1.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "hope_votes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_vote_once",
                        columnNames = {"application_id", "user_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class HopeVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id")
    private Long voteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private HopeApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}