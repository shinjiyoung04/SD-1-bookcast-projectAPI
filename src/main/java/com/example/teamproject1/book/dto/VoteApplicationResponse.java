package com.example.teamproject1.book.dto;

import com.example.teamproject1.book.entity.enums.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class VoteApplicationResponse {

    private Long applicationId;

    private Long bookId;

    private String isbn;

    private String title;

    private String author;

    private String publisher;

    private LocalDate publishedDate;

    private String categoryName;

    private String thumbnailUrl;

    private String libraryName;

    private String reason;

    private ApplicationStatus status;

    private Long voteCount;

    private LocalDateTime createdAt;

    private Boolean alreadyVoted;
}