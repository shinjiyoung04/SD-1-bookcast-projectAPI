package com.example.teamproject1.book.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BookSyncResponse {

    private Integer requestedCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private String message;
}