package com.example.teamproject1.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateApplicationRequest {

    @NotNull
    private Long userId;

    @NotBlank
    private String isbn;

    @NotBlank
    private String title;

    private String author;

    private String publisher;

    private LocalDate publishedDate;

    private Long categoryId;

    private Long libraryId;

    private String libCode;

    private String libraryName;

    private String libraryAddress;

    private String libraryPhone;

    @NotBlank
    private String reason;
}