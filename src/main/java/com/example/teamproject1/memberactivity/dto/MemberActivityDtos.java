package com.example.teamproject1.memberactivity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class MemberActivityDtos {

    private MemberActivityDtos() {
    }

    public record BookInteractionRequest(
            @NotBlank(message = "도서명을 입력해주세요.")
            @Size(max = 255, message = "도서명은 255자 이하로 입력해주세요.")
            String title,

            @NotBlank(message = "저자명을 입력해주세요.")
            @Size(max = 150, message = "저자명은 150자 이하로 입력해주세요.")
            String author,

            @Size(max = 150, message = "출판사명은 150자 이하로 입력해주세요.")
            String publisher,

            @Size(max = 500, message = "썸네일 주소는 500자 이하로 입력해주세요.")
            String thumbnailUrl
    ) {
    }

    public record ReviewCreateRequest(
            @Min(value = 1, message = "평점은 1점 이상이어야 합니다.")
            @Max(value = 5, message = "평점은 5점 이하여야 합니다.")
            int score,

            @NotBlank(message = "리뷰 내용을 입력해주세요.")
            @Size(max = 3000, message = "리뷰 내용은 3000자 이하로 입력해주세요.")
            String content,

            @NotBlank(message = "도서명을 입력해주세요.")
            @Size(max = 255, message = "도서명은 255자 이하로 입력해주세요.")
            String title,

            @NotBlank(message = "저자명을 입력해주세요.")
            @Size(max = 150, message = "저자명은 150자 이하로 입력해주세요.")
            String author,

            @Size(max = 150, message = "출판사명은 150자 이하로 입력해주세요.")
            String publisher,

            @Size(max = 500, message = "썸네일 주소는 500자 이하로 입력해주세요.")
            String thumbnailUrl
    ) {
    }

    public record BookUserStateResponse(
            String isbn,
            boolean liked,
            boolean wishlisted,
            long likeCount
    ) {
    }

    public record ReviewResponse(
            Long reviewId,
            Long userId,
            String nickname,
            int score,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record LikedBookResponse(
            String isbn,
            String title,
            String author,
            String publisher,
            String thumbnailUrl,
            LocalDateTime likedAt
    ) {
    }

    public record VotedApplicationResponse(
            Long applicationId,
            String title,
            String author,
            String publisher,
            String isbn,
            String thumbnailUrl,
            String libraryName,
            String status,
            long voteCount,
            LocalDateTime votedAt,
            LocalDateTime createdAt
    ) {
    }
}
