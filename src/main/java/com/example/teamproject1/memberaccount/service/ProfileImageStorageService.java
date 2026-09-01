package com.example.teamproject1.memberaccount.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageStorageService {

    private static final long MAX_FILE_SIZE =
            5L * 1024L * 1024L;

    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS =
            Map.of(
                    "image/jpeg", "jpg",
                    "image/png", "png",
                    "image/webp", "webp"
            );

    @Value("${app.upload.profile-dir:uploads/profile}")
    private String configuredUploadDirectory;

    @Getter
    private Path uploadRoot;

    @PostConstruct
    public void initialize() {
        try {
            uploadRoot = Path.of(
                            configuredUploadDirectory
                    )
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(uploadRoot);

            log.info(
                    "[ProfileImageStorageService] 프로필 이미지 저장 경로: {}",
                    uploadRoot
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "프로필 이미지 저장 폴더를 생성하지 못했습니다.",
                    exception
            );
        }
    }

    public String store(
            Long userId,
            MultipartFile file
    ) {
        validateFile(file);

        String contentType =
                file.getContentType() == null
                        ? ""
                        : file.getContentType()
                                .toLowerCase(Locale.ROOT);

        String extension =
                CONTENT_TYPE_EXTENSIONS.get(
                        contentType
                );

        String filename =
                "user-"
                        + userId
                        + "-"
                        + UUID.randomUUID()
                        + "."
                        + extension;

        Path target =
                uploadRoot.resolve(filename)
                        .normalize();

        if (!target.startsWith(uploadRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 파일 경로입니다."
            );
        }

        try {
            Files.copy(
                    file.getInputStream(),
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "프로필 이미지 저장에 실패했습니다.",
                    exception
            );
        }

        return "/uploads/profile/" + filename;
    }

    public void deleteLocalFile(String publicUrl) {
        if (!StringUtils.hasText(publicUrl)) {
            return;
        }

        String normalizedUrl =
                publicUrl.trim();

        String prefix =
                "/uploads/profile/";

        if (!normalizedUrl.startsWith(prefix)) {
            return;
        }

        String filename =
                normalizedUrl.substring(
                        prefix.length()
                );

        if (!StringUtils.hasText(filename)
                || filename.contains("/")
                || filename.contains("\\")
                || filename.contains("..")) {
            log.warn(
                    "[ProfileImageStorageService] 삭제하지 않은 비정상 파일명: {}",
                    filename
            );

            return;
        }

        Path target =
                uploadRoot.resolve(filename)
                        .normalize();

        if (!target.startsWith(uploadRoot)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.warn(
                    "[ProfileImageStorageService] 기존 프로필 이미지 삭제 실패: {}",
                    target,
                    exception
            );
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "업로드할 이미지 파일을 선택해주세요."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "프로필 이미지는 5MB 이하만 업로드할 수 있습니다."
            );
        }

        String contentType =
                file.getContentType() == null
                        ? ""
                        : file.getContentType()
                                .toLowerCase(Locale.ROOT);

        if (!CONTENT_TYPE_EXTENSIONS.containsKey(
                contentType
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "JPG, PNG, WEBP 이미지 파일만 업로드할 수 있습니다."
            );
        }
    }
}
