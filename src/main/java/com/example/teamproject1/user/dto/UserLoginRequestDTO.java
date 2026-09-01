package com.example.teamproject1.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserLoginRequestDTO {

    private String loginId;
    private String password;
}