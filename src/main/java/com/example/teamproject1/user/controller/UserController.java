package com.example.teamproject1.user.controller;

import com.example.teamproject1.user.dto.KakaoLoginRequestDTO;
import com.example.teamproject1.user.dto.KakaoLoginResponseDTO;
import com.example.teamproject1.user.dto.UserLoginRequestDTO;
import com.example.teamproject1.user.dto.UserRegisterRequestDTO;
import com.example.teamproject1.user.dto.UserResponseDTO;
import com.example.teamproject1.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponseDTO> register(@ModelAttribute UserRegisterRequestDTO dto) {
        return ResponseEntity.ok(userService.register(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponseDTO> login(@RequestBody UserLoginRequestDTO dto) {
        return ResponseEntity.ok(userService.login(dto));
    }

    @PostMapping("/kakao/login")
    public ResponseEntity<KakaoLoginResponseDTO> kakaoLogin(
            @RequestBody KakaoLoginRequestDTO dto
    ) {
        return ResponseEntity.ok(userService.kakaoLogin(dto.getCode()));
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("User API 연결 성공");
    }
}