package com.example.teamproject1.user.service;

import com.example.teamproject1.user.dto.KakaoLoginResponseDTO;
import com.example.teamproject1.user.dto.UserLoginRequestDTO;
import com.example.teamproject1.user.dto.UserRegisterRequestDTO;
import com.example.teamproject1.user.dto.UserResponseDTO;
import com.example.teamproject1.user.entity.User;
import com.example.teamproject1.user.entity.UserRole;
import com.example.teamproject1.user.entity.UserStatus;
import com.example.teamproject1.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    private final String uploadDir =
            System.getProperty("user.dir")
                    + "/uploads/profiles/";

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    // 회원가입-신규 회원의 비밀번호는 반드시 BCrypt로 암호화하여 저장
    public UserResponseDTO register(
            UserRegisterRequestDTO dto
    ) {
        validateRegisterRequest(dto);

        String loginId =
                dto.getLoginId().trim();

        String email =
                dto.getEmail() == null
                        ? null
                        : dto.getEmail().trim();

        if (userRepository.existsByLoginId(loginId)) {
            throw new RuntimeException(
                    "이미 사용 중인 아이디입니다."
            );
        }

        if (email != null
                && !email.isBlank()
                && userRepository.existsByEmail(email)) {
            throw new RuntimeException(
                    "이미 사용 중인 이메일입니다."
            );
        }

        String profileImageUrl =
                saveProfileImage(
                        dto.getProfileImage()
                );

        UserRole role =
                dto.getRole() == null
                        ? UserRole.USER
                        : dto.getRole();

        // 사용자가 입력한 일반 비밀번호를 BCrypt로 암호화하여 DB에 저장
        String encodedPassword =
                passwordEncoder.encode(
                        dto.getPassword()
                );

        User user = User.builder()
                .loginId(loginId)
                .password(encodedPassword)
                .name(dto.getName().trim())
                .nickname(
                        normalizeNullable(
                                dto.getNickname()
                        )
                )
                .email(
                        normalizeNullable(email)
                )
                .profileImageUrl(
                        profileImageUrl
                )
                .role(role)
                .status(UserStatus.ACTIVE)
                .provider("LOCAL")
                .build();

        User savedUser =
                userRepository.save(user);

        return UserResponseDTO.fromEntity(
                savedUser
        );
    }

    /**
     * 일반 로그인
     *
     * BCrypt 비밀번호:
     * passwordEncoder.matches()로 비교
     *
     * 기존 평문 비밀번호:
     * 평문으로 한 번 비교한 뒤 로그인 성공 시
     * BCrypt로 자동 변환
     */
    public UserResponseDTO login(
            UserLoginRequestDTO dto
    ) {
        validateLoginRequest(dto);

        String loginId =
                dto.getLoginId().trim();

        String rawPassword =
                dto.getPassword();

        User user = userRepository
                .findByLoginId(loginId)
                .orElseThrow(
                        () -> new RuntimeException(
                                "아이디 또는 비밀번호가 일치하지 않습니다."
                        )
                );

        if (user.getStatus()
                != UserStatus.ACTIVE) {
            throw new RuntimeException(
                    getUnavailableAccountMessage(
                            user.getStatus()
                    )
            );
        }

        if (user.getProvider() != null
                && !"LOCAL".equalsIgnoreCase(
                user.getProvider()
        )
                && !"GENERAL".equalsIgnoreCase(
                user.getProvider()
        )) {
            throw new RuntimeException(
                    "소셜 로그인으로 가입한 계정입니다."
            );
        }

        String storedPassword =
                user.getPassword();

        if (!matchesPassword(
                rawPassword,
                storedPassword
        )) {
            throw new RuntimeException(
                    "아이디 또는 비밀번호가 일치하지 않습니다."
            );
        }

        // 기존에 평문으로 저장된 계정은 정상 로그인 성공 후 BCrypt로 변환
        if (!isBcryptPassword(
                storedPassword
        )) {
            String encodedPassword =
                    passwordEncoder.encode(
                            rawPassword
                    );

            user.setPassword(
                    encodedPassword
            );

            userRepository.save(user);

            log.info(
                    "[UserService] 기존 평문 비밀번호를 BCrypt로 변환했습니다. userId={}, loginId={}",
                    user.getUserId(),
                    user.getLoginId()
            );
        }

        return buildLoginResponse(
                user
        );
    }

    // 카카오 로그인

    public KakaoLoginResponseDTO kakaoLogin(
            String code
    ) {
        if (code == null
                || code.isBlank()) {
            throw new RuntimeException(
                    "카카오 인증 코드가 없습니다."
            );
        }

        String accessToken =
                getKakaoAccessToken(code);

        Map<String, Object> kakaoUserInfo =
                getKakaoUserInfo(
                        accessToken
                );

        String kakaoId =
                String.valueOf(
                        kakaoUserInfo.get("id")
                );

        Map<String, Object> kakaoAccount =
                (Map<String, Object>)
                        kakaoUserInfo.get(
                                "kakao_account"
                        );

        Map<String, Object> profile =
                null;

        Map<String, Object> properties =
                null;

        if (kakaoAccount != null) {
            profile =
                    (Map<String, Object>)
                            kakaoAccount.get(
                                    "profile"
                            );
        }

        if (kakaoUserInfo.get(
                "properties"
        ) != null) {
            properties =
                    (Map<String, Object>)
                            kakaoUserInfo.get(
                                    "properties"
                            );
        }

        String email =
                "kakao_"
                        + kakaoId
                        + "@kakao.local";

        if (kakaoAccount != null
                && kakaoAccount.get(
                "email"
        ) != null) {
            email =
                    String.valueOf(
                            kakaoAccount.get(
                                    "email"
                            )
                    );
        }

        String nickname =
                "카카오사용자";

        if (profile != null
                && profile.get(
                "nickname"
        ) != null) {
            nickname =
                    String.valueOf(
                            profile.get(
                                    "nickname"
                            )
                    );
        } else if (properties != null
                && properties.get(
                "nickname"
        ) != null) {
            nickname =
                    String.valueOf(
                            properties.get(
                                    "nickname"
                            )
                    );
        }

        String profileImageUrl =
                null;

        if (profile != null
                && profile.get(
                "profile_image_url"
        ) != null) {
            profileImageUrl =
                    String.valueOf(
                            profile.get(
                                    "profile_image_url"
                            )
                    );
        } else if (properties != null
                && properties.get(
                "profile_image"
        ) != null) {
            profileImageUrl =
                    String.valueOf(
                            properties.get(
                                    "profile_image"
                            )
                    );
        }

        User existingUser =
                userRepository
                        .findByProviderAndProviderId(
                                "KAKAO",
                                kakaoId
                        )
                        .orElse(null);

        if (existingUser != null) {
            validateSocialAccountStatus(
                    existingUser
            );

            return KakaoLoginResponseDTO
                    .fromEntity(
                            existingUser,
                            false
                    );
        }

        User emailUser =
                userRepository
                        .findByEmail(email)
                        .orElse(null);

        if (emailUser != null) {
            if (emailUser.getStatus()
                    != UserStatus.ACTIVE) {
                throw new RuntimeException(
                        getUnavailableAccountMessage(
                                emailUser.getStatus()
                        )
                );
            }

            emailUser.setProvider(
                    "KAKAO"
            );

            emailUser.setProviderId(
                    kakaoId
            );

            if (emailUser.getProfileImageUrl()
                    == null
                    || emailUser
                    .getProfileImageUrl()
                    .isBlank()) {
                emailUser.setProfileImageUrl(
                        profileImageUrl
                );
            }

            User savedEmailUser =
                    userRepository.save(
                            emailUser
                    );

            return KakaoLoginResponseDTO
                    .fromEntity(
                            savedEmailUser,
                            false
                    );
        }

        /*
         * 카카오 계정의 비밀번호는 사용되지 않지만
         * DB에는 평문 대신 BCrypt 해시를 저장합니다.
         */
        String randomPassword =
                UUID.randomUUID()
                        .toString();

        String encodedRandomPassword =
                passwordEncoder.encode(
                        randomPassword
                );

        User newUser = User.builder()
                .loginId(
                        "kakao_" + kakaoId
                )
                .password(
                        encodedRandomPassword
                )
                .name(nickname)
                .nickname(nickname)
                .email(email)
                .profileImageUrl(
                        profileImageUrl
                )
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .provider("KAKAO")
                .providerId(kakaoId)
                .build();

        User savedUser =
                userRepository.save(
                        newUser
                );

        return KakaoLoginResponseDTO
                .fromEntity(
                        savedUser,
                        true
                );
    }

    // 사용자가 입력한 원본 비밀번호와 DB에 저장된 비밀번호를 비교
    private boolean matchesPassword(
            String rawPassword,
            String storedPassword
    ) {
        if (rawPassword == null
                || storedPassword == null
                || storedPassword.isBlank()) {
            return false;
        }

        String passwordForComparison =
                storedPassword;

        /*
         * {bcrypt}$2a$... 형태 지원
         */
        if (passwordForComparison.startsWith(
                "{bcrypt}"
        )) {
            passwordForComparison =
                    passwordForComparison.substring(
                            "{bcrypt}".length()
                    );
        }

        // 정상 BCrypt 비밀번호

        if (isBcryptPassword(
                passwordForComparison
        )) {
            try {
                return passwordEncoder.matches(
                        rawPassword,
                        passwordForComparison
                );
            } catch (
                    IllegalArgumentException exception
            ) {
                log.warn(
                        "[UserService] BCrypt 비밀번호 비교 실패",
                        exception
                );

                return false;
            }
        }

        if (passwordForComparison.startsWith("$")
                || passwordForComparison.startsWith("{")) {
            return false;
        }

        // 기존 평문 비밀번호 호환 처리

        return MessageDigest.isEqual(
                rawPassword.getBytes(
                        StandardCharsets.UTF_8
                ),
                passwordForComparison.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    // BCrypt 비밀번호 형식 확인
    private boolean isBcryptPassword(
            String password
    ) {
        if (password == null) {
            return false;
        }

        String normalizedPassword =
                password.startsWith(
                        "{bcrypt}"
                )
                        ? password.substring(
                        "{bcrypt}".length()
                )
                        : password;

        return normalizedPassword.matches(
                "^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$"
        );
    }

    private void validateRegisterRequest(
            UserRegisterRequestDTO dto
    ) {
        if (dto == null) {
            throw new RuntimeException(
                    "회원가입 정보가 없습니다."
            );
        }

        if (dto.getLoginId() == null
                || dto.getLoginId()
                .isBlank()) {
            throw new RuntimeException(
                    "아이디를 입력해주세요."
            );
        }

        if (dto.getPassword() == null
                || dto.getPassword()
                .isEmpty()) {
            throw new RuntimeException(
                    "비밀번호를 입력해주세요."
            );
        }

        if (dto.getName() == null
                || dto.getName()
                .isBlank()) {
            throw new RuntimeException(
                    "이름을 입력해주세요."
            );
        }
    }

    private void validateLoginRequest(
            UserLoginRequestDTO dto
    ) {
        if (dto == null) {
            throw new RuntimeException(
                    "로그인 정보가 없습니다."
            );
        }

        if (dto.getLoginId() == null
                || dto.getLoginId()
                .isBlank()) {
            throw new RuntimeException(
                    "아이디를 입력해주세요."
            );
        }

        if (dto.getPassword() == null
                || dto.getPassword()
                .isEmpty()) {
            throw new RuntimeException(
                    "비밀번호를 입력해주세요."
            );
        }
    }

    private String getUnavailableAccountMessage(
            UserStatus status
    ) {
        if (status == UserStatus.DELETED) {
            return "탈퇴한 계정입니다.";
        }

        if (status == UserStatus.BLOCKED) {
            return "이용이 정지된 계정입니다.";
        }

        return "사용할 수 없는 계정입니다.";
    }

    private void validateSocialAccountStatus(
            User user
    ) {
        if (user.getStatus()
                != UserStatus.ACTIVE) {
            throw new RuntimeException(
                    getUnavailableAccountMessage(
                            user.getStatus()
                    )
            );
        }
    }

    private String normalizeNullable(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String getKakaoAccessToken(
            String code
    ) {
        RestTemplate restTemplate =
                new RestTemplate();

        String tokenUrl =
                "https://kauth.kakao.com/oauth/token";

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        MultiValueMap<String, String> params =
                new LinkedMultiValueMap<>();

        params.add(
                "grant_type",
                "authorization_code"
        );

        params.add(
                "client_id",
                kakaoRestApiKey
        );

        params.add(
                "redirect_uri",
                kakaoRedirectUri
        );

        params.add(
                "code",
                code
        );

        HttpEntity<
                MultiValueMap<String, String>
                > request =
                new HttpEntity<>(
                        params,
                        headers
                );

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        tokenUrl,
                        request,
                        Map.class
                );

        Map<String, Object> body =
                response.getBody();

        if (body == null
                || body.get(
                "access_token"
        ) == null) {
            throw new RuntimeException(
                    "카카오 access token 발급 실패"
            );
        }

        return String.valueOf(
                body.get(
                        "access_token"
                )
        );
    }

    private Map<String, Object> getKakaoUserInfo(
            String accessToken
    ) {
        RestTemplate restTemplate =
                new RestTemplate();

        String userInfoUrl =
                "https://kapi.kakao.com/v2/user/me";

        HttpHeaders headers =
                new HttpHeaders();

        headers.setBearerAuth(
                accessToken
        );

        HttpEntity<Void> request =
                new HttpEntity<>(
                        headers
                );

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        userInfoUrl,
                        HttpMethod.GET,
                        request,
                        Map.class
                );

        Map<String, Object> body =
                response.getBody();

        if (body == null) {
            throw new RuntimeException(
                    "카카오 사용자 정보 조회 실패"
            );
        }

        return body;
    }

    private String saveProfileImage(
            MultipartFile file
    ) {
        if (file == null
                || file.isEmpty()) {
            return null;
        }

        validateProfileImage(file);

        try {
            File folder =
                    new File(uploadDir);

            if (!folder.exists()
                    && !folder.mkdirs()) {
                throw new RuntimeException(
                        "프로필 이미지 저장 폴더를 생성하지 못했습니다."
                );
            }

            String originalFilename =
                    file.getOriginalFilename();

            String extension =
                    getSafeImageExtension(
                            file,
                            originalFilename
                    );

            String savedFilename =
                    UUID.randomUUID()
                            + extension;

            File savedFile =
                    new File(
                            uploadDir
                                    + savedFilename
                    );

            file.transferTo(
                    savedFile
            );

            return "/uploads/profiles/"
                    + savedFilename;
        } catch (IOException exception) {
            throw new RuntimeException(
                    "프로필 이미지 저장에 실패했습니다.",
                    exception
            );
        }
    }

    private void validateProfileImage(
            MultipartFile file
    ) {
        long maxSize =
                5L * 1024L * 1024L;

        if (file.getSize() > maxSize) {
            throw new RuntimeException(
                    "프로필 이미지는 5MB 이하만 업로드할 수 있습니다."
            );
        }

        String contentType =
                file.getContentType();

        if (!"image/jpeg".equals(
                contentType
        )
                && !"image/png".equals(
                contentType
        )
                && !"image/webp".equals(
                contentType
        )) {
            throw new RuntimeException(
                    "JPG, PNG, WEBP 이미지만 업로드할 수 있습니다."
            );
        }
    }

    private String getSafeImageExtension(
            MultipartFile file,
            String originalFilename
    ) {
        String contentType =
                file.getContentType();

        if ("image/jpeg".equals(
                contentType
        )) {
            return ".jpg";
        }

        if ("image/png".equals(
                contentType
        )) {
            return ".png";
        }

        if ("image/webp".equals(
                contentType
        )) {
            return ".webp";
        }

        if (originalFilename != null
                && originalFilename
                .contains(".")) {
            return originalFilename
                    .substring(
                            originalFilename
                                    .lastIndexOf(".")
                    );
        }

        return "";
    }

    private UserResponseDTO buildLoginResponse(
            User user
    ) {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    u.managed_library_id,

                    COALESCE(
                        u.managed_library_code,
                        l.lib_code
                    ) AS managed_library_code,

                    l.library_name
                        AS managed_library_name

                FROM users u

                LEFT JOIN libraries l
                  ON (
                        l.library_id =
                            u.managed_library_id

                        OR (
                            u.managed_library_id IS NULL
                            AND l.lib_code =
                                u.managed_library_code
                        )
                  )

                WHERE u.user_id = ?
                """,
                (resultSet, rowNumber) ->
                        UserResponseDTO.fromEntity(
                                user,
                                resultSet.getObject(
                                        "managed_library_id",
                                        Long.class
                                ),
                                resultSet.getString(
                                        "managed_library_code"
                                ),
                                resultSet.getString(
                                        "managed_library_name"
                                )
                        ),
                user.getUserId()
        );
    }

}