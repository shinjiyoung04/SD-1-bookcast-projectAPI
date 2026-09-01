package com.example.teamproject1.memberaccount.controller;

import com.example.teamproject1.memberaccount.dto.MemberLibraryResponse;
import com.example.teamproject1.memberaccount.dto.MemberProfileResponse;
import com.example.teamproject1.memberaccount.dto.MemberProfileUpdateRequest;
import com.example.teamproject1.memberaccount.dto.MemberWithdrawRequest;
import com.example.teamproject1.memberaccount.dto.MemberWithdrawResponse;
import com.example.teamproject1.memberaccount.dto.PasswordVerifyRequest;
import com.example.teamproject1.memberaccount.dto.PasswordVerifyResponse;
import com.example.teamproject1.memberaccount.service.MemberAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/member-account")
public class MemberAccountController {

    private final MemberAccountService
            memberAccountService;

    @PostMapping("/{userId}/verify-password")
    public ResponseEntity<PasswordVerifyResponse>
    verifyPassword(
            @PathVariable Long userId,
            @Valid
            @RequestBody
            PasswordVerifyRequest request
    ) {
        return ResponseEntity.ok(
                memberAccountService.verifyPassword(
                        userId,
                        request.password()
                )
        );
    }

    @GetMapping("/{userId}")
    public ResponseEntity<MemberProfileResponse>
    getProfile(
            @PathVariable Long userId,
            @RequestHeader(
                    "X-Profile-Verification"
            )
            String verificationToken
    ) {
        return ResponseEntity.ok(
                memberAccountService.getProfile(
                        userId,
                        verificationToken
                )
        );
    }

    @GetMapping("/{userId}/libraries")
    public ResponseEntity<List<MemberLibraryResponse>>
    getEditableLibraries(
            @PathVariable Long userId,
            @RequestHeader(
                    "X-Profile-Verification"
            )
            String verificationToken
    ) {
        return ResponseEntity.ok(
                memberAccountService
                        .getEditableLibraries(
                                userId,
                                verificationToken
                        )
        );
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<MemberProfileResponse>
    updateProfile(
            @PathVariable Long userId,
            @RequestHeader(
                    "X-Profile-Verification"
            )
            String verificationToken,
            @Valid
            @RequestBody
            MemberProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(
                memberAccountService.updateProfile(
                        userId,
                        verificationToken,
                        request
                )
        );
    }

    @PostMapping(
            value = "/{userId}/profile-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<MemberProfileResponse>
    updateProfileImage(
            @PathVariable Long userId,
            @RequestHeader(
                    "X-Profile-Verification"
            )
            String verificationToken,
            @RequestPart("file")
            MultipartFile file
    ) {
        return ResponseEntity.ok(
                memberAccountService.updateProfileImage(
                        userId,
                        verificationToken,
                        file
                )
        );
    }

    @DeleteMapping("/{userId}/profile-image")
    public ResponseEntity<MemberProfileResponse>
    deleteProfileImage(
            @PathVariable Long userId,
            @RequestHeader(
                    "X-Profile-Verification"
            )
            String verificationToken
    ) {
        return ResponseEntity.ok(
                memberAccountService.deleteProfileImage(
                        userId,
                        verificationToken
                )
        );
    }

    @PatchMapping("/{userId}/withdraw")
    public ResponseEntity<MemberWithdrawResponse>
    withdraw(
            @PathVariable Long userId,
            @Valid
            @RequestBody
            MemberWithdrawRequest request
    ) {
        return ResponseEntity.ok(
                memberAccountService.withdraw(
                        userId,
                        request.verificationToken(),
                        request.password()
                )
        );
    }
}
