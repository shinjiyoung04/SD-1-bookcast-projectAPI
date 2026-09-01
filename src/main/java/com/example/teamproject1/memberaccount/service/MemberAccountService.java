package com.example.teamproject1.memberaccount.service;

import com.example.teamproject1.common.service.ManagedLibrarySyncService;
import com.example.teamproject1.common.service.ManagedLibrarySyncService.ManagedLibrary;
import com.example.teamproject1.memberaccount.dto.MemberLibraryResponse;
import com.example.teamproject1.memberaccount.dto.MemberProfileResponse;
import com.example.teamproject1.memberaccount.dto.MemberProfileUpdateRequest;
import com.example.teamproject1.memberaccount.dto.MemberWithdrawResponse;
import com.example.teamproject1.memberaccount.dto.PasswordVerifyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAccountService {

    private static final Set<String> ADMIN_ROLES =
            Set.of(
                    "ADMIN",
                    "MASTER_ADMIN"
            );

    private final JdbcTemplate jdbcTemplate;

    private final PasswordEncoder passwordEncoder;

    private final ProfileVerificationService
            verificationService;

    private final ProfileImageStorageService
            profileImageStorageService;

    private final ManagedLibrarySyncService
            managedLibrarySyncService;

    @Transactional
    public PasswordVerifyResponse verifyPassword(
            Long userId,
            String rawPassword
    ) {
        validatePasswordInput(rawPassword);

        UserAccount account =
                getActiveUserAccount(userId);

        validateLocalAccount(account);

        if (!matchesPassword(
                userId,
                rawPassword,
                account.password()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "비밀번호가 일치하지 않습니다."
            );
        }

        ProfileVerificationService.TokenIssue issue =
                verificationService.issue(
                        userId
                );

        return new PasswordVerifyResponse(
                issue.token(),
                issue.expiresAt()
        );
    }

    public MemberProfileResponse getProfile(
            Long userId,
            String verificationToken
    ) {
        verificationService.validate(
                userId,
                verificationToken
        );

        return getActiveProfile(userId);
    }

    public List<MemberLibraryResponse> getEditableLibraries(
            Long userId,
            String verificationToken
    ) {
        verificationService.validate(
                userId,
                verificationToken
        );

        UserAccount account =
                getActiveUserAccount(userId);

        requireAdminRole(
                account.role()
        );

        return jdbcTemplate.query(
                """
                SELECT
                    library_id,
                    library_name,
                    address,
                    phone
                FROM libraries
                ORDER BY library_name
                """,
                (resultSet, rowNumber) ->
                        new MemberLibraryResponse(
                                resultSet.getLong(
                                        "library_id"
                                ),
                                resultSet.getString(
                                        "library_name"
                                ),
                                resultSet.getString(
                                        "address"
                                ),
                                resultSet.getString(
                                        "phone"
                                )
                        )
        );
    }

    @Transactional
    public MemberProfileResponse updateProfile(
            Long userId,
            String verificationToken,
            MemberProfileUpdateRequest request
    ) {
        verificationService.validate(
                userId,
                verificationToken
        );

        UserAccount account =
                getActiveUserAccount(userId);

        String normalizedName =
                normalizeRequired(
                        request.name(),
                        "이름"
                );

        String normalizedEmail =
                normalizeRequired(
                        request.email(),
                        "이메일"
                );

        validateDuplicateEmail(
                userId,
                normalizedEmail
        );

        String normalizedGender =
                normalizeGender(
                        request.gender()
                );

        ManagedLibraryAssignment managedLibrary =
                resolveManagedLibrary(
                        account.role(),
                        request
                );

        try {
            int updated =
                    jdbcTemplate.update(
                            """
                            UPDATE users
                            SET
                                name = ?,
                                nickname = ?,
                                email = ?,
                                address = ?,
                                birth_date = ?,
                                gender = ?,
                                managed_library_id = ?,
                                managed_library_code = ?,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE user_id = ?
                              AND status = 'ACTIVE'
                            """,
                            normalizedName,
                            normalizeNullable(
                                    request.nickname()
                            ),
                            normalizedEmail,
                            normalizeNullable(
                                    request.address()
                            ),
                            request.birthDate() == null
                                    ? null
                                    : Date.valueOf(
                                    request.birthDate()
                            ),
                            normalizedGender,
                            managedLibrary.libraryId(),
                            managedLibrary.libraryCode(),
                            userId
                    );

            if (updated == 0) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "수정할 회원을 찾을 수 없습니다."
                );
            }

            MemberProfileResponse response =
                    getActiveProfile(userId);

            verificationService.invalidate(
                    verificationToken
            );

            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.error(
                    "[MemberAccountService] 회원정보 수정 실패. userId={}",
                    userId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "회원정보 수정 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    @Transactional
    public MemberProfileResponse updateProfileImage(
            Long userId,
            String verificationToken,
            MultipartFile file
    ) {
        verificationService.validate(
                userId,
                verificationToken
        );

        UserAccount account =
                getActiveUserAccount(userId);

        String newPublicUrl =
                profileImageStorageService.store(
                        userId,
                        file
                );

        try {
            int updated =
                    jdbcTemplate.update(
                            """
                            UPDATE users
                            SET
                                profile_image_url = ?,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE user_id = ?
                              AND status = 'ACTIVE'
                            """,
                            newPublicUrl,
                            userId
                    );

            if (updated == 0) {
                profileImageStorageService
                        .deleteLocalFile(
                                newPublicUrl
                        );

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "프로필 이미지를 변경할 회원을 찾을 수 없습니다."
                );
            }

            profileImageStorageService
                    .deleteLocalFile(
                            account.profileImageUrl()
                    );

            return getActiveProfile(userId);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            profileImageStorageService
                    .deleteLocalFile(
                            newPublicUrl
                    );

            log.error(
                    "[MemberAccountService] 프로필 이미지 DB 반영 실패. userId={}",
                    userId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "프로필 이미지 변경 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    @Transactional
    public MemberProfileResponse deleteProfileImage(
            Long userId,
            String verificationToken
    ) {
        verificationService.validate(
                userId,
                verificationToken
        );

        UserAccount account =
                getActiveUserAccount(userId);

        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE users
                        SET
                            profile_image_url = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ?
                          AND status = 'ACTIVE'
                        """,
                        userId
                );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "프로필 이미지를 변경할 회원을 찾을 수 없습니다."
            );
        }

        profileImageStorageService
                .deleteLocalFile(
                        account.profileImageUrl()
                );

        return getActiveProfile(userId);
    }

    @Transactional
    public MemberWithdrawResponse withdraw(
            Long userId,
            String verificationToken,
            String rawPassword
    ) {
        verificationService.validate(
                userId,
                verificationToken
        );

        validatePasswordInput(rawPassword);

        UserAccount account =
                getActiveUserAccount(userId);

        validateLocalAccount(account);

        if (!matchesPassword(
                userId,
                rawPassword,
                account.password()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "비밀번호가 일치하지 않습니다."
            );
        }

        validateMasterAdminWithdrawal(
                account
        );

        try {
            int updatedUser =
                    jdbcTemplate.update(
                            """
                            UPDATE users
                            SET
                                status = 'DELETED',
                                profile_image_url = NULL,
                                managed_library_id = NULL,
                                managed_library_code = NULL,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE user_id = ?
                              AND status = 'ACTIVE'
                            """,
                            userId
                    );

            if (updatedUser == 0) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "탈퇴할 회원을 찾을 수 없습니다."
                );
            }

            hideMemberReviews(userId);
            hideHopeApplications(userId);
            deactivateHopeVotes(userId);
            cancelPendingPromotionRequests(
                    userId
            );

            profileImageStorageService
                    .deleteLocalFile(
                            account.profileImageUrl()
                    );

            verificationService.invalidateUser(
                    userId
            );

            return new MemberWithdrawResponse(
                    userId,
                    "DELETED",
                    "회원탈퇴가 완료되었습니다."
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.error(
                    "[MemberAccountService] 회원탈퇴 실패. userId={}",
                    userId,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "회원탈퇴 처리 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private ManagedLibraryAssignment resolveManagedLibrary(
            String role,
            MemberProfileUpdateRequest request
    ) {
        String normalizedRole =
                normalizeRole(role);

        if (!ADMIN_ROLES.contains(
                normalizedRole
        )) {
            return new ManagedLibraryAssignment(
                    null,
                    null
            );
        }

        String libraryCode =
                normalizeNullable(
                        request
                                .managedLibraryCode()
                );

        String libraryName =
                normalizeNullable(
                        request
                                .managedLibraryName()
                );

        if ("MASTER_ADMIN".equals(
                normalizedRole
        ) && libraryCode == null) {
            return new ManagedLibraryAssignment(
                    null,
                    null
            );
        }

        if (libraryCode == null
                || libraryName == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "일반 관리자는 담당 도서관을 반드시 선택해야 합니다."
            );
        }

        ManagedLibrary library =
                managedLibrarySyncService
                        .syncLibrary(
                                libraryCode,
                                libraryName,
                                request
                                        .managedLibraryAddress(),
                                request
                                        .managedLibraryPhone()
                        );

        return new ManagedLibraryAssignment(
                library.libraryId(),
                library.libraryCode()
        );
    }

    private void validateMasterAdminWithdrawal(
            UserAccount account
    ) {
        if (!"MASTER_ADMIN".equals(
                normalizeRole(
                        account.role()
                )
        )) {
            return;
        }

        Long masterAdminCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM users
                        WHERE role = 'MASTER_ADMIN'
                          AND status = 'ACTIVE'
                        """,
                        Long.class
                );

        if (masterAdminCount != null
                && masterAdminCount <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "마지막 최고 관리자는 탈퇴할 수 없습니다."
            );
        }
    }

    private boolean matchesPassword(
            Long userId,
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

        if (passwordForComparison.startsWith(
                "{bcrypt}"
        )) {
            passwordForComparison =
                    passwordForComparison.substring(
                            "{bcrypt}".length()
                    );
        }

        if (isBcryptPassword(
                passwordForComparison
        )) {
            try {
                return passwordEncoder.matches(
                        rawPassword,
                        passwordForComparison
                );
            } catch (IllegalArgumentException exception) {
                log.warn(
                        "[MemberAccountService] BCrypt 비교 실패. userId={}",
                        userId,
                        exception
                );

                return false;
            }
        }

        if (passwordForComparison.startsWith("$")
                || passwordForComparison.startsWith("{")) {
            return false;
        }

        boolean matches =
                MessageDigest.isEqual(
                        rawPassword.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        passwordForComparison.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        if (matches) {
            upgradeLegacyPassword(
                    userId,
                    rawPassword
            );
        }

        return matches;
    }

    private void upgradeLegacyPassword(
            Long userId,
            String rawPassword
    ) {
        jdbcTemplate.update(
                """
                UPDATE users
                SET
                    password = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND status = 'ACTIVE'
                """,
                passwordEncoder.encode(
                        rawPassword
                ),
                userId
        );

        log.info(
                "[MemberAccountService] 평문 비밀번호를 BCrypt로 변환했습니다. userId={}",
                userId
        );
    }

    private boolean isBcryptPassword(
            String password
    ) {
        return password != null
                && password.matches(
                "^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$"
        );
    }

    private void validateLocalAccount(
            UserAccount account
    ) {
        if (!StringUtils.hasText(
                account.provider()
        )) {
            return;
        }

        String provider =
                account.provider()
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if ("LOCAL".equals(provider)
                || "GENERAL".equals(provider)) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "소셜 로그인 계정은 일반 비밀번호를 확인할 수 없습니다."
        );
    }

    private void requireAdminRole(
            String role
    ) {
        if (!ADMIN_ROLES.contains(
                normalizeRole(role)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "관리자만 관리 도서관을 선택할 수 있습니다."
            );
        }
    }

    private UserAccount getActiveUserAccount(
            Long userId
    ) {
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT
                        user_id,
                        password,
                        role,
                        status,
                        provider,
                        profile_image_url
                    FROM users
                    WHERE user_id = ?
                      AND status = 'ACTIVE'
                    """,
                    (resultSet, rowNumber) ->
                            new UserAccount(
                                    resultSet.getLong(
                                            "user_id"
                                    ),
                                    resultSet.getString(
                                            "password"
                                    ),
                                    resultSet.getString(
                                            "role"
                                    ),
                                    resultSet.getString(
                                            "status"
                                    ),
                                    resultSet.getString(
                                            "provider"
                                    ),
                                    resultSet.getString(
                                            "profile_image_url"
                                    )
                            ),
                    userId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "활성 상태의 회원을 찾을 수 없습니다."
            );
        }
    }

    private MemberProfileResponse getActiveProfile(
            Long userId
    ) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT
                        u.user_id,
                        u.login_id,
                        u.name,
                        u.nickname,
                        u.email,
                        u.profile_image_url,
                        u.address,
                        u.birth_date,
                        u.gender,
                        u.role,
                        u.status,
                        u.provider,
                        u.managed_library_id,
                        u.managed_library_code,
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
                      AND u.status = 'ACTIVE'
                    """,
                    this::mapProfile,
                    userId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "회원정보를 찾을 수 없습니다."
            );
        }
    }

    private MemberProfileResponse mapProfile(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        Date birthDate =
                resultSet.getDate(
                        "birth_date"
                );

        return new MemberProfileResponse(
                resultSet.getLong(
                        "user_id"
                ),
                resultSet.getString(
                        "login_id"
                ),
                resultSet.getString(
                        "name"
                ),
                resultSet.getString(
                        "nickname"
                ),
                resultSet.getString(
                        "email"
                ),
                resultSet.getString(
                        "profile_image_url"
                ),
                resultSet.getString(
                        "address"
                ),
                birthDate == null
                        ? null
                        : birthDate.toLocalDate(),
                resultSet.getString(
                        "gender"
                ),
                resultSet.getString(
                        "role"
                ),
                resultSet.getString(
                        "status"
                ),
                resultSet.getString(
                        "provider"
                ),
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
        );
    }

    private void validateDuplicateEmail(
            Long userId,
            String email
    ) {
        Integer duplicateCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM users
                        WHERE email = ?
                          AND user_id <> ?
                          AND status <> 'DELETED'
                        """,
                        Integer.class,
                        email,
                        userId
                );

        if (duplicateCount != null
                && duplicateCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 사용 중인 이메일입니다."
            );
        }
    }

    private void hideMemberReviews(
            Long userId
    ) {
        if (!tableExists(
                "book_reviews"
        )) {
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE book_reviews
                SET
                    is_deleted = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """,
                userId
        );
    }

    private void hideHopeApplications(
            Long userId
    ) {
        if (!tableExists(
                "hope_applications"
        )
                || !columnExists(
                "hope_applications",
                "is_hidden"
        )) {
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE hope_applications
                SET is_hidden = TRUE
                WHERE user_id = ?
                """,
                userId
        );
    }

    private void deactivateHopeVotes(
            Long userId
    ) {
        if (!tableExists(
                "hope_votes"
        )
                || !columnExists(
                "hope_votes",
                "active"
        )) {
            return;
        }

        if (columnExists(
                "hope_votes",
                "canceled_at"
        )
                && columnExists(
                "hope_votes",
                "updated_at"
        )) {
            jdbcTemplate.update(
                    """
                    UPDATE hope_votes
                    SET
                        active = FALSE,
                        canceled_at = COALESCE(
                            canceled_at,
                            CURRENT_TIMESTAMP
                        ),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE user_id = ?
                    """,
                    userId
            );

            return;
        }

        jdbcTemplate.update(
                """
                UPDATE hope_votes
                SET active = FALSE
                WHERE user_id = ?
                """,
                userId
        );
    }

    private void cancelPendingPromotionRequests(
            Long userId
    ) {
        if (!tableExists(
                "promotion_requests"
        )) {
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE promotion_requests
                SET
                    status = 'CANCELED',
                    processed_at = COALESCE(
                        processed_at,
                        CURRENT_TIMESTAMP
                    ),
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND status = 'PENDING'
                """,
                userId
        );
    }

    private boolean tableExists(
            String tableName
    ) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                        """,
                        Integer.class,
                        tableName
                );

        return count != null
                && count > 0;
    }

    private boolean columnExists(
            String tableName,
            String columnName
    ) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                        Integer.class,
                        tableName,
                        columnName
                );

        return count != null
                && count > 0;
    }

    private void validatePasswordInput(
            String password
    ) {
        if (password == null
                || password.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "비밀번호를 입력해주세요."
            );
        }
    }

    private String normalizeRequired(
            String value,
            String fieldName
    ) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName
                            + "을(를) 입력해주세요."
            );
        }

        return value.trim();
    }

    private String normalizeNullable(
            String value
    ) {
        return StringUtils.hasText(value)
                ? value.trim()
                : null;
    }

    private String normalizeGender(
            String value
    ) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized =
                value.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!normalized.matches(
                "MALE|FEMALE|OTHER"
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 성별 값입니다."
            );
        }

        return normalized;
    }

    private String normalizeRole(
            String value
    ) {
        return String.valueOf(value)
                .trim()
                .toUpperCase(
                        Locale.ROOT
                )
                .replaceFirst(
                        "^ROLE_",
                        ""
                );
    }

    private record ManagedLibraryAssignment(
            Long libraryId,
            String libraryCode
    ) {
    }

    private record UserAccount(
            Long userId,
            String password,
            String role,
            String status,
            String provider,
            String profileImageUrl
    ) {
    }
}