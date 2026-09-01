package com.example.teamproject1.memberaccount.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MemberProfileUpdateRequest(
        @NotBlank(
                message = "이름을 입력해주세요."
        )
        @Size(
                max = 50,
                message = "이름은 50자 이하로 입력해주세요."
        )
        String name,

        @Size(
                max = 50,
                message = "닉네임은 50자 이하로 입력해주세요."
        )
        String nickname,

        @NotBlank(
                message = "이메일을 입력해주세요."
        )
        @Email(
                message = "올바른 이메일 형식이 아닙니다."
        )
        @Size(
                max = 100,
                message = "이메일은 100자 이하로 입력해주세요."
        )
        String email,

        @Size(
                max = 255,
                message = "주소는 255자 이하로 입력해주세요."
        )
        String address,

        LocalDate birthDate,

        @Size(
                max = 20,
                message = "성별 값은 20자 이하로 입력해주세요."
        )
        String gender,

        @Size(
                max = 50,
                message = "도서관 코드는 50자 이하로 입력해주세요."
        )
        String managedLibraryCode,

        @Size(
                max = 255,
                message = "도서관명은 255자 이하로 입력해주세요."
        )
        String managedLibraryName,

        @Size(
                max = 500,
                message = "도서관 주소는 500자 이하로 입력해주세요."
        )
        String managedLibraryAddress,

        @Size(
                max = 100,
                message = "도서관 전화번호는 100자 이하로 입력해주세요."
        )
        String managedLibraryPhone
) {
}