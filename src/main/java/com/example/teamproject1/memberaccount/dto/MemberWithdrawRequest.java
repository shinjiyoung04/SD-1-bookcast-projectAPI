package com.example.teamproject1.memberaccount.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberWithdrawRequest(
        @NotBlank(message = "본인 확인 토큰이 필요합니다.")
        String verificationToken,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
