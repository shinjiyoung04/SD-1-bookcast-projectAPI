package com.example.teamproject1.user.dto;

import com.example.teamproject1.user.entity.UserRole;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class UserRegisterRequestDTO {

    private String loginId;
    private String password;
    private String name;
    private String nickname;
    private String email;
    private UserRole role;
    private MultipartFile profileImage;
}
