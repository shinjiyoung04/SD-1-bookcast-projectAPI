package com.example.teamproject1.book.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExternalBookExistResponse {

    private String libCode;
    private String isbn13;

    private String hasBook;
    private String loanAvailable;

    private Boolean isOwned;
    private Boolean isLoanAvailable;

    private String loanStatus;
    private Boolean canApplyHope;
    private String message;
}