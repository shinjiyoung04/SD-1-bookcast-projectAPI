package com.example.teamproject1.book.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoteCreateResponse {

    private Long applicationId;

    private Long voteCount;

    private String message;
}