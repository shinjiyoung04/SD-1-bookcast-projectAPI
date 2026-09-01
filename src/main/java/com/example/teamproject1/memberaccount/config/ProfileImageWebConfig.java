package com.example.teamproject1.memberaccount.config;

import com.example.teamproject1.memberaccount.service.ProfileImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class ProfileImageWebConfig
        implements WebMvcConfigurer {

    private final ProfileImageStorageService
            profileImageStorageService;

    @Override
    public void addResourceHandlers(
            ResourceHandlerRegistry registry
    ) {
        // 회원정보 수정 화면에서 업로드한 이미지

        registry
                .addResourceHandler(
                        "/uploads/profile/**"
                )
                .addResourceLocations(
                        profileImageStorageService
                                .getUploadRoot()
                                .toUri()
                                .toString()
                );

        // 기존 회원가입에서 업로드한 이미지

        Path legacyUploadRoot =
                Path.of(
                                System.getProperty(
                                        "user.dir"
                                ),
                                "uploads",
                                "profiles"
                        )
                        .toAbsolutePath()
                        .normalize();

        registry
                .addResourceHandler(
                        "/uploads/profiles/**"
                )
                .addResourceLocations(
                        legacyUploadRoot
                                .toUri()
                                .toString()
                );
    }
}
