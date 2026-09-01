package com.example.teamproject1.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicationLibraryResolveRequest(

        @NotBlank(
                message =
                        "도서관 코드를 입력해주세요."
        )
        @Size(
                max = 50,
                message =
                        "도서관 코드는 50자 이하로 입력해주세요."
        )
        String libraryCode,

        @NotBlank(
                message =
                        "도서관명을 입력해주세요."
        )
        @Size(
                max = 255,
                message =
                        "도서관명은 255자 이하로 입력해주세요."
        )
        String libraryName,

        @Size(
                max = 500,
                message =
                        "도서관 주소는 500자 이하로 입력해주세요."
        )
        String address,

        @Size(
                max = 100,
                message =
                        "도서관 전화번호는 100자 이하로 입력해주세요."
        )
        String phone
) {
}