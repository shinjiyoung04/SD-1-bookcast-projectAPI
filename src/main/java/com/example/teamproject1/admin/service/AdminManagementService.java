package com.example.teamproject1.admin.service;

import com.example.teamproject1.admin.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminManagementService {

    private static final Set<String> ADMIN_ROLES =
            Set.of(
                    "ADMIN",
                    "MASTER_ADMIN"
            );

    private static final Set<String> USER_ROLES =
            Set.of(
                    "USER",
                    "ADMIN",
                    "MASTER_ADMIN"
            );

    private static final Set<String> USER_STATUSES =
            Set.of(
                    "ACTIVE",
                    "BLOCKED",
                    "DELETED"
            );

    private static final Set<String> APPLICATION_STATUSES =
            Set.of(
                    "PENDING",
                    "APPROVED",
                    "REJECTED",
                    "CANCELED"
            );

    private final JdbcTemplate jdbcTemplate;

    /**
     * 관리자 본인정보와 신규 희망도서 신청 건수
     */
    public AdminSelfResponse getAdminSelf(
            Long requesterUserId
    ) {
        AdminAccount admin =
                requireAdmin(
                        requesterUserId
                );

        Long pendingCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM hope_applications a

                        JOIN users applicant
                          ON applicant.user_id =
                             a.user_id
                         AND applicant.status =
                             'ACTIVE'

                        WHERE a.status =
                              'PENDING'
                          AND a.is_hidden =
                              FALSE
                        """
                                + adminScopeSql(
                                admin
                        ),
                        Long.class,
                        adminScopeParams(
                                admin
                        ).toArray()
                );

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
                        u.role,
                        u.status,
                        u.managed_library_id,
                        u.managed_library_code,

                        COALESCE(
                            id_library.library_name,
                            code_library.library_name
                        ) AS library_name

                    FROM users u

                    LEFT JOIN libraries id_library
                      ON id_library.library_id =
                         u.managed_library_id

                    LEFT JOIN libraries code_library
                      ON id_library.library_id IS NULL
                     AND code_library.lib_code =
                         u.managed_library_code

                    WHERE u.user_id = ?
                      AND u.status = 'ACTIVE'
                    """,
                    (resultSet, rowNumber) ->
                            new AdminSelfResponse(
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
                                            "role"
                                    ),
                                    resultSet.getString(
                                            "status"
                                    ),
                                    resultSet.getObject(
                                            "managed_library_id",
                                            Long.class
                                    ),
                                    resultSet.getString(
                                            "managed_library_code"
                                    ),
                                    resultSet.getString(
                                            "library_name"
                                    ),
                                    pendingCount == null
                                            ? 0L
                                            : pendingCount
                            ),
                    requesterUserId
            );
        } catch (
                EmptyResultDataAccessException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "관리자 정보를 찾을 수 없습니다."
            );
        }
    }

    /**
     * 관리자 대시보드 통계
     *
     * 일반 관리자:
     * 담당 도서관 데이터만 집계
     *
     * 최고 관리자:
     * 전체 도서관 데이터 집계
     */
    public AdminDashboardResponse getDashboard(
            Long requesterUserId
    ) {
        AdminAccount admin =
                requireAdmin(
                        requesterUserId
                );

        String scope =
                adminScopeSql(admin);

        Object[] scopeParams =
                adminScopeParams(
                        admin
                ).toArray();

        Long pending =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM hope_applications a

                        JOIN users applicant
                          ON applicant.user_id =
                             a.user_id
                         AND applicant.status =
                             'ACTIVE'

                        WHERE a.status =
                              'PENDING'
                          AND a.is_hidden =
                              FALSE
                        """
                                + scope,
                        Long.class,
                        scopeParams
                );

        Long today =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM hope_applications a

                        JOIN users applicant
                          ON applicant.user_id =
                             a.user_id
                         AND applicant.status =
                             'ACTIVE'

                        WHERE a.status =
                              'PENDING'
                          AND a.is_hidden =
                              FALSE
                          AND DATE(
                              a.created_at
                          ) = CURRENT_DATE
                        """
                                + scope,
                        Long.class,
                        scopeParams
                );

        Long votes =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM hope_votes vote

                        JOIN hope_applications a
                          ON a.application_id =
                             vote.application_id

                        JOIN users applicant
                          ON applicant.user_id =
                             a.user_id
                         AND applicant.status =
                             'ACTIVE'

                        WHERE vote.active =
                              TRUE
                          AND a.is_hidden =
                              FALSE
                          AND a.status =
                              'PENDING'
                        """
                                + scope,
                        Long.class,
                        scopeParams
                );

        Double averageProbability =
                jdbcTemplate.queryForObject(
                        """
                        SELECT AVG(
                            latest.approval_probability
                        )
                        FROM hope_applications a

                        JOIN users applicant
                          ON applicant.user_id =
                             a.user_id
                         AND applicant.status =
                             'ACTIVE'

                        LEFT JOIN ai_predictions latest
                          ON latest.prediction_id = (
                              SELECT MAX(
                                  prediction_history.prediction_id
                              )
                              FROM ai_predictions
                                  prediction_history
                              WHERE
                                  prediction_history.application_id =
                                      a.application_id
                          )

                        WHERE a.status =
                              'PENDING'
                          AND a.is_hidden =
                              FALSE
                        """
                                + scope,
                        Double.class,
                        scopeParams
                );

        return new AdminDashboardResponse(
                pending == null
                        ? 0L
                        : pending,

                today == null
                        ? 0L
                        : today,

                votes == null
                        ? 0L
                        : votes,

                averageProbability == null
                        ? 0.0
                        : averageProbability
        );
    }

    /**
     * 희망도서 신청 목록
     */
    public AdminApplicationPageResponse getApplications(
            Long requesterUserId,
            String keyword,
            String status,
            String sort,
            Integer page,
            Integer pageSize
    ) {
        AdminAccount admin =
                requireAdmin(
                        requesterUserId
                );

        int safePage =
                page == null || page < 1
                        ? 1
                        : page;

        int safePageSize =
                pageSize == null
                        ? 10
                        : Math.max(
                        1,
                        Math.min(
                                pageSize,
                                50
                        )
                );

        String normalizedStatus =
                normalizeApplicationStatusFilter(
                        status
                );

        String normalizedSort =
                normalizeApplicationSort(
                        sort
                );

        StringBuilder where =
                new StringBuilder(
                        """
                        WHERE a.is_hidden =
                              FALSE
                          AND applicant.status =
                              'ACTIVE'
                        """
                );

        List<Object> parameters =
                new ArrayList<>();

        if (normalizedStatus != null) {
            where.append(
                    " AND a.status = ? "
            );

            parameters.add(
                    normalizedStatus
            );
        }

        if (StringUtils.hasText(
                keyword
        )) {
            String like =
                    "%"
                            + keyword.trim()
                            + "%";

            where.append(
                    """
                    AND (
                        a.title LIKE ?
                        OR a.author LIKE ?
                        OR a.isbn LIKE ?
                        OR applicant.login_id LIKE ?
                        OR applicant.name LIKE ?
                        OR COALESCE(
                            library.library_name,
                            ''
                        ) LIKE ?
                    )
                    """
            );

            for (int index = 0;
                 index < 6;
                 index++) {
                parameters.add(like);
            }
        }

        where.append(
                adminScopeSql(
                        admin
                )
        );

        parameters.addAll(
                adminScopeParams(
                        admin
                )
        );

        String countSql =
                """
                SELECT COUNT(*)
                FROM hope_applications a

                JOIN users applicant
                  ON applicant.user_id =
                     a.user_id

                LEFT JOIN libraries library
                  ON library.library_id =
                     a.library_id
                """
                        + where;

        Long total =
                jdbcTemplate.queryForObject(
                        countSql,
                        Long.class,
                        parameters.toArray()
                );

        long totalElements =
                total == null
                        ? 0L
                        : total;

        int totalPages =
                totalElements == 0
                        ? 0
                        : (int) Math.ceil(
                        (double) totalElements
                                / safePageSize
                );

        int offset =
                (safePage - 1)
                        * safePageSize;

        String orderBy =
                switch (normalizedSort) {
                    case "OLDEST" ->
                            """
                            ORDER BY
                                a.created_at ASC,
                                a.application_id ASC
                            """;

                    case "VOTES" ->
                            """
                            ORDER BY
                                vote_count DESC,
                                a.created_at DESC
                            """;

                    case "AI" ->
                            """
                            ORDER BY
                                approval_probability DESC,
                                a.created_at DESC
                            """;

                    default ->
                            """
                            ORDER BY
                                a.created_at DESC,
                                a.application_id DESC
                            """;
                };

        String listSql =
                baseApplicationSelect()
                        + where
                        + orderBy
                        + " LIMIT ? OFFSET ? ";

        List<Object> queryParameters =
                new ArrayList<>(
                        parameters
                );

        queryParameters.add(
                safePageSize
        );

        queryParameters.add(
                offset
        );

        List<AdminApplicationItemResponse>
                content =
                jdbcTemplate.query(
                        listSql,
                        this::mapApplication,
                        queryParameters.toArray()
                );

        return new AdminApplicationPageResponse(
                content,
                safePage,
                safePageSize,
                totalElements,
                totalPages
        );
    }

    /**
     * 희망도서 신청 상세
     */
    public AdminApplicationItemResponse getApplication(
            Long requesterUserId,
            Long applicationId
    ) {
        if (applicationId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "희망도서 신청 번호가 필요합니다."
            );
        }

        AdminAccount admin =
                requireAdmin(
                        requesterUserId
                );

        String sql =
                baseApplicationSelect()
                        + """
                        WHERE a.application_id = ?
                          AND a.is_hidden =
                              FALSE
                          AND applicant.status =
                              'ACTIVE'
                        """
                        + adminScopeSql(
                        admin
                );

        List<Object> parameters =
                new ArrayList<>();

        parameters.add(
                applicationId
        );

        parameters.addAll(
                adminScopeParams(
                        admin
                )
        );

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    this::mapApplication,
                    parameters.toArray()
            );
        } catch (
                EmptyResultDataAccessException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "담당 도서관에서 조회할 수 있는 희망도서 신청이 없습니다."
            );
        }
    }

    /**
     * 희망도서 승인 또는 거절
     */
    @Transactional
    public AdminApplicationItemResponse decideApplication(
            Long applicationId,
            AdminApplicationDecisionRequest request
    ) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "희망도서 처리 정보가 필요합니다."
            );
        }

        if (!StringUtils.hasText(
                request.decision()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "처리 결과를 입력해주세요."
            );
        }

        AdminAccount admin =
                requireAdmin(
                        request.requesterUserId()
                );

        String decision =
                request.decision()
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!Set.of(
                "APPROVED",
                "REJECTED"
        ).contains(decision)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "APPROVED 또는 REJECTED만 사용할 수 있습니다."
            );
        }

        String comment =
                normalizeNullable(
                        request.adminComment()
                );

        if ("REJECTED".equals(decision)
                && !StringUtils.hasText(
                comment
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "거절 사유를 입력해주세요."
            );
        }

        /*
         * 상세 조회 과정에서 담당 도서관 권한을 검사합니다.
         */
        AdminApplicationItemResponse current =
                getApplication(
                        admin.userId(),
                        applicationId
                );

        if (!"PENDING".equals(
                current.status()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 처리된 신청입니다."
            );
        }

        String updateSql =
                """
                UPDATE hope_applications AS a
                SET
                    status = ?,
                    admin_id = ?,
                    admin_comment = ?,
                    processed_at =
                        CURRENT_TIMESTAMP
                WHERE a.application_id = ?
                  AND a.status =
                      'PENDING'
                  AND a.is_hidden =
                      FALSE
                """
                        + adminScopeSql(
                        admin
                );

        List<Object> updateParameters =
                new ArrayList<>();

        updateParameters.add(
                decision
        );

        updateParameters.add(
                admin.userId()
        );

        updateParameters.add(
                comment
        );

        updateParameters.add(
                applicationId
        );

        updateParameters.addAll(
                adminScopeParams(
                        admin
                )
        );

        int updated =
                jdbcTemplate.update(
                        updateSql,
                        updateParameters.toArray()
                );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "담당 도서관 신청이 아니거나 이미 처리된 신청입니다."
            );
        }

        jdbcTemplate.update(
                """
                INSERT INTO admin_activity_logs (
                    admin_id,
                    target_type,
                    target_id,
                    action,
                    description
                )
                VALUES (
                    ?,
                    'APPLICATION',
                    ?,
                    ?,
                    ?
                )
                """,
                admin.userId(),
                applicationId,
                decision,
                comment
        );

        return getApplication(
                admin.userId(),
                applicationId
        );
    }

    /**
     * 최고 관리자용 회원 목록
     */
    public AdminMemberPageResponse getMembers(
            Long requesterUserId,
            String keyword,
            String role,
            String status,
            Integer page,
            Integer pageSize
    ) {
        requireMasterAdmin(
                requesterUserId
        );

        int safePage =
                page == null || page < 1
                        ? 1
                        : page;

        int safePageSize =
                pageSize == null
                        ? 10
                        : Math.max(
                        1,
                        Math.min(
                                pageSize,
                                50
                        )
                );

        String normalizedRole =
                normalizeOptionalRole(
                        role
                );

        String normalizedStatus =
                normalizeOptionalStatus(
                        status
                );

        StringBuilder where =
                new StringBuilder(
                        " WHERE 1 = 1 "
                );

        List<Object> parameters =
                new ArrayList<>();

        if (normalizedRole != null) {
            where.append(
                    " AND u.role = ? "
            );

            parameters.add(
                    normalizedRole
            );
        }

        if (normalizedStatus != null) {
            where.append(
                    " AND u.status = ? "
            );

            parameters.add(
                    normalizedStatus
            );
        }

        if (StringUtils.hasText(
                keyword
        )) {
            String like =
                    "%"
                            + keyword.trim()
                            + "%";

            where.append(
                    """
                    AND (
                        u.login_id LIKE ?
                        OR u.name LIKE ?
                        OR COALESCE(
                            u.nickname,
                            ''
                        ) LIKE ?
                        OR COALESCE(
                            u.email,
                            ''
                        ) LIKE ?
                    )
                    """
            );

            for (int index = 0;
                 index < 4;
                 index++) {
                parameters.add(like);
            }
        }

        Long total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) "
                                + "FROM users u "
                                + where,
                        Long.class,
                        parameters.toArray()
                );

        long totalElements =
                total == null
                        ? 0L
                        : total;

        int totalPages =
                totalElements == 0
                        ? 0
                        : (int) Math.ceil(
                        (double) totalElements
                                / safePageSize
                );

        int offset =
                (safePage - 1)
                        * safePageSize;

        String sql =
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

                    COALESCE(
                        id_library.library_name,
                        code_library.library_name
                    ) AS managed_library_name,

                    u.created_at,
                    u.updated_at

                FROM users u

                LEFT JOIN libraries id_library
                  ON id_library.library_id =
                     u.managed_library_id

                LEFT JOIN libraries code_library
                  ON id_library.library_id IS NULL
                 AND code_library.lib_code =
                     u.managed_library_code
                """
                        + where
                        + """
                        ORDER BY
                            CASE u.role
                                WHEN 'MASTER_ADMIN'
                                    THEN 1
                                WHEN 'ADMIN'
                                    THEN 2
                                ELSE 3
                            END,
                            u.created_at DESC
                        LIMIT ? OFFSET ?
                        """;

        List<Object> queryParameters =
                new ArrayList<>(
                        parameters
                );

        queryParameters.add(
                safePageSize
        );

        queryParameters.add(
                offset
        );

        List<AdminMemberItemResponse> content =
                jdbcTemplate.query(
                        sql,
                        this::mapMember,
                        queryParameters.toArray()
                );

        return new AdminMemberPageResponse(
                content,
                safePage,
                safePageSize,
                totalElements,
                totalPages
        );
    }

    /**
     * 최고 관리자가 회원 등급 변경
     */
    @Transactional
    public AdminMemberItemResponse updateMemberRole(
            Long targetUserId,
            AdminMemberRoleUpdateRequest request
    ) {
        if (request == null
                || !StringUtils.hasText(
                request.role()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "변경할 회원 등급이 필요합니다."
            );
        }

        AdminAccount requester =
                requireMasterAdmin(
                        request.requesterUserId()
                );

        if (requester.userId()
                .equals(targetUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "본인의 최고 관리자 권한은 이 화면에서 변경할 수 없습니다."
            );
        }

        String role =
                normalizeRole(
                        request.role()
                );

        if (!USER_ROLES.contains(
                role
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 회원 등급입니다."
            );
        }

        AdminMemberItemResponse target =
                getMemberForMaster(
                        requester.userId(),
                        targetUserId
                );

        if (!"ACTIVE".equals(
                target.status()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "활성 상태 회원의 등급만 변경할 수 있습니다."
            );
        }

        if ("MASTER_ADMIN".equals(
                target.role()
        )
                && !"MASTER_ADMIN".equals(
                role
        )) {
            Long masterCount =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM users
                            WHERE role =
                                  'MASTER_ADMIN'
                              AND status =
                                  'ACTIVE'
                            """,
                            Long.class
                    );

            if (masterCount != null
                    && masterCount <= 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "최소 한 명의 최고 관리자는 유지되어야 합니다."
                );
            }
        }

        LibraryAssignment library =
                null;

        if ("ADMIN".equals(role)) {
            if (request.managedLibraryId()
                    == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "일반 관리자의 담당 도서관을 선택해주세요."
                );
            }

            library =
                    findLibraryAssignment(
                            request
                                    .managedLibraryId()
                    );
        }

        Long managedLibraryId =
                library == null
                        ? null
                        : library.libraryId();

        String managedLibraryCode =
                library == null
                        ? null
                        : library.libCode();

        jdbcTemplate.update(
                """
                UPDATE users
                SET
                    role = ?,
                    managed_library_id = ?,
                    managed_library_code = ?,
                    updated_at =
                        CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND status =
                      'ACTIVE'
                """,
                role,
                managedLibraryId,
                managedLibraryCode,
                targetUserId
        );

        jdbcTemplate.update(
                """
                INSERT INTO admin_activity_logs (
                    admin_id,
                    target_type,
                    target_id,
                    action,
                    description
                )
                VALUES (
                    ?,
                    'USER',
                    ?,
                    'ROLE_CHANGE',
                    ?
                )
                """,
                requester.userId(),
                targetUserId,
                target.role()
                        + " -> "
                        + role
        );

        return getMemberForMaster(
                requester.userId(),
                targetUserId
        );
    }

    public List<AdminLibraryResponse> getLibraries(
            Long requesterUserId
    ) {
        requireMasterAdmin(
                requesterUserId
        );

        return jdbcTemplate.query(
                """
                SELECT
                    library_id,
                    library_name,
                    address,
                    phone
                FROM libraries
                WHERE lib_code IS NOT NULL
                  AND TRIM(lib_code) <> ''
                ORDER BY library_name
                """,
                (resultSet, rowNumber) ->
                        new AdminLibraryResponse(
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

    private AdminMemberItemResponse getMemberForMaster(
            Long requesterUserId,
            Long targetUserId
    ) {
        requireMasterAdmin(
                requesterUserId
        );

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

                        COALESCE(
                            id_library.library_name,
                            code_library.library_name
                        ) AS managed_library_name,

                        u.created_at,
                        u.updated_at

                    FROM users u

                    LEFT JOIN libraries id_library
                      ON id_library.library_id =
                         u.managed_library_id

                    LEFT JOIN libraries code_library
                      ON id_library.library_id IS NULL
                     AND code_library.lib_code =
                         u.managed_library_code

                    WHERE u.user_id = ?
                    """,
                    this::mapMember,
                    targetUserId
            );
        } catch (
                EmptyResultDataAccessException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "회원을 찾을 수 없습니다."
            );
        }
    }

    private AdminAccount requireAdmin(
            Long userId
    ) {
        AdminAccount account =
                getActiveAccount(
                        userId
                );

        if (!ADMIN_ROLES.contains(
                account.role()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "관리자만 접근할 수 있습니다."
            );
        }

        return account;
    }

    private AdminAccount requireMasterAdmin(
            Long userId
    ) {
        AdminAccount account =
                getActiveAccount(
                        userId
                );

        if (!"MASTER_ADMIN".equals(
                account.role()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "최고 관리자만 접근할 수 있습니다."
            );
        }

        return account;
    }

    // 관리자 계정 조회
    private AdminAccount getActiveAccount(
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
                        role,
                        status,
                        managed_library_id,
                        managed_library_code
                    FROM users
                    WHERE user_id = ?
                      AND status =
                          'ACTIVE'
                    """,
                    (resultSet, rowNumber) ->
                            new AdminAccount(
                                    resultSet.getLong(
                                            "user_id"
                                    ),
                                    normalizeRole(
                                            resultSet.getString(
                                                    "role"
                                            )
                                    ),
                                    resultSet.getString(
                                            "status"
                                    ),
                                    resultSet.getObject(
                                            "managed_library_id",
                                            Long.class
                                    ),
                                    normalizeNullable(
                                            resultSet.getString(
                                                    "managed_library_code"
                                            )
                                    )
                            ),
                    userId
            );
        } catch (
                EmptyResultDataAccessException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "활성 상태의 로그인 사용자를 찾을 수 없습니다."
            );
        }
    }

    // 담당 도서관 조회
    private LibraryAssignment findLibraryAssignment(
            Long libraryId
    ) {
        try {
            LibraryAssignment library =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT
                                library_id,
                                lib_code,
                                library_name
                            FROM libraries
                            WHERE library_id = ?
                            """,
                            (resultSet, rowNumber) ->
                                    new LibraryAssignment(
                                            resultSet.getLong(
                                                    "library_id"
                                            ),
                                            resultSet.getString(
                                                    "lib_code"
                                            ),
                                            resultSet.getString(
                                                    "library_name"
                                            )
                                    ),
                            libraryId
                    );

            if (library == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "선택한 도서관이 존재하지 않습니다."
                );
            }

            if (!StringUtils.hasText(
                    library.libCode()
            )) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "선택한 도서관에 정보나루 코드가 등록되어 있지 않습니다."
                );
            }

            return library;
        } catch (
                EmptyResultDataAccessException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "선택한 도서관이 존재하지 않습니다."
            );
        }
    }

    private String adminScopeSql(
            AdminAccount admin
    ) {
        if ("MASTER_ADMIN".equals(
                admin.role()
        )) {
            return "";
        }

        boolean hasLibraryId =
                admin.managedLibraryId()
                        != null;

        boolean hasLibraryCode =
                StringUtils.hasText(
                        admin.managedLibraryCode()
                );

        if (!hasLibraryId
                && !hasLibraryCode) {
            return " AND 1 = 0 ";
        }

        if (hasLibraryId
                && hasLibraryCode) {
            return """
                    AND (
                        a.library_id = ?

                        OR EXISTS (
                            SELECT 1
                            FROM libraries
                                scoped_library
                            WHERE
                                scoped_library.library_id =
                                    a.library_id
                              AND scoped_library.lib_code =
                                    ?
                        )
                    )
                    """;
        }

        if (hasLibraryId) {
            return """
                    AND a.library_id = ?
                    """;
        }

        return """
                AND EXISTS (
                    SELECT 1
                    FROM libraries
                        scoped_library
                    WHERE
                        scoped_library.library_id =
                            a.library_id
                      AND scoped_library.lib_code =
                            ?
                )
                """;
    }

    private List<Object> adminScopeParams(
            AdminAccount admin
    ) {
        List<Object> parameters =
                new ArrayList<>();

        if ("MASTER_ADMIN".equals(
                admin.role()
        )) {
            return parameters;
        }

        if (admin.managedLibraryId()
                != null) {
            parameters.add(
                    admin.managedLibraryId()
            );
        }

        if (StringUtils.hasText(
                admin.managedLibraryCode()
        )) {
            parameters.add(
                    admin.managedLibraryCode()
                            .trim()
            );
        }

        return parameters;
    }

    private String baseApplicationSelect() {
        return """
                SELECT
                    a.application_id,

                    a.user_id
                        AS applicant_user_id,

                    applicant.login_id
                        AS applicant_login_id,

                    applicant.name
                        AS applicant_name,

                    a.title,
                    a.author,
                    book.publisher,
                    a.isbn,
                    book.published_date,
                    a.library_id,

                    library.library_name,

                    a.reason,
                    a.status,
                    a.admin_comment,
                    a.created_at,
                    a.processed_at,

                    COALESCE(
                        vote_summary.vote_count,
                        0
                    ) AS vote_count,

                    prediction.prediction_id,
                    prediction.approval_probability,
                    prediction.popularity_score,
                    prediction.vote_adjustment,
                    prediction.final_score,
                    prediction.model_version

                FROM hope_applications a

                JOIN users applicant
                  ON applicant.user_id =
                     a.user_id

                LEFT JOIN books book
                  ON book.book_id =
                     a.book_id

                LEFT JOIN libraries library
                  ON library.library_id =
                     a.library_id

                LEFT JOIN (
                    SELECT
                        application_id,
                        COUNT(*) AS vote_count
                    FROM hope_votes
                    WHERE active =
                          TRUE
                    GROUP BY application_id
                ) vote_summary
                  ON vote_summary.application_id =
                     a.application_id

                LEFT JOIN ai_predictions prediction
                  ON prediction.prediction_id = (
                      SELECT MAX(
                          prediction_history.prediction_id
                      )
                      FROM ai_predictions
                          prediction_history
                      WHERE
                          prediction_history.application_id =
                              a.application_id
                  )
                """;
    }

    private AdminApplicationItemResponse mapApplication(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new AdminApplicationItemResponse(
                resultSet.getLong(
                        "application_id"
                ),
                resultSet.getLong(
                        "applicant_user_id"
                ),
                resultSet.getString(
                        "applicant_login_id"
                ),
                resultSet.getString(
                        "applicant_name"
                ),
                resultSet.getString(
                        "title"
                ),
                resultSet.getString(
                        "author"
                ),
                resultSet.getString(
                        "publisher"
                ),
                resultSet.getString(
                        "isbn"
                ),
                toLocalDate(
                        resultSet.getDate(
                                "published_date"
                        )
                ),
                resultSet.getObject(
                        "library_id",
                        Long.class
                ),
                resultSet.getString(
                        "library_name"
                ),
                resultSet.getString(
                        "reason"
                ),
                resultSet.getString(
                        "status"
                ),
                resultSet.getString(
                        "admin_comment"
                ),
                toLocalDateTime(
                        resultSet.getTimestamp(
                                "created_at"
                        )
                ),
                toLocalDateTime(
                        resultSet.getTimestamp(
                                "processed_at"
                        )
                ),
                resultSet.getLong(
                        "vote_count"
                ),
                resultSet.getObject(
                        "prediction_id",
                        Long.class
                ),
                resultSet.getObject(
                        "approval_probability",
                        Double.class
                ),
                resultSet.getObject(
                        "popularity_score",
                        Double.class
                ),
                resultSet.getObject(
                        "vote_adjustment",
                        Double.class
                ),
                resultSet.getObject(
                        "final_score",
                        Double.class
                ),
                resultSet.getString(
                        "model_version"
                )
        );
    }

    private AdminMemberItemResponse mapMember(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new AdminMemberItemResponse(
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
                toLocalDate(
                        resultSet.getDate(
                                "birth_date"
                        )
                ),
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
                ),
                toLocalDateTime(
                        resultSet.getTimestamp(
                                "created_at"
                        )
                ),
                toLocalDateTime(
                        resultSet.getTimestamp(
                                "updated_at"
                        )
                )
        );
    }

    private String normalizeApplicationStatusFilter(
            String status
    ) {
        if (!StringUtils.hasText(
                status
        )
                || "ALL".equalsIgnoreCase(
                status
        )) {
            return null;
        }

        String normalized =
                status.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!APPLICATION_STATUSES.contains(
                normalized
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 신청 상태입니다."
            );
        }

        return normalized;
    }

    private String normalizeApplicationSort(
            String sort
    ) {
        String normalized =
                StringUtils.hasText(sort)
                        ? sort.trim()
                        .toUpperCase(
                                Locale.ROOT
                        )
                        : "LATEST";

        return Set.of(
                "LATEST",
                "OLDEST",
                "VOTES",
                "AI"
        ).contains(normalized)
                ? normalized
                : "LATEST";
    }

    private String normalizeOptionalRole(
            String role
    ) {
        if (!StringUtils.hasText(
                role
        )
                || "ALL".equalsIgnoreCase(
                role
        )) {
            return null;
        }

        String normalized =
                normalizeRole(role);

        if (!USER_ROLES.contains(
                normalized
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 회원 등급입니다."
            );
        }

        return normalized;
    }

    private String normalizeOptionalStatus(
            String status
    ) {
        if (!StringUtils.hasText(
                status
        )
                || "ALL".equalsIgnoreCase(
                status
        )) {
            return null;
        }

        String normalized =
                status.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!USER_STATUSES.contains(
                normalized
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 회원 상태입니다."
            );
        }

        return normalized;
    }

    private String normalizeRole(
            String role
    ) {
        return String.valueOf(role)
                .trim()
                .toUpperCase(
                        Locale.ROOT
                )
                .replaceFirst(
                        "^ROLE_",
                        ""
                );
    }

    private String normalizeNullable(
            String value
    ) {
        return StringUtils.hasText(value)
                ? value.trim()
                : null;
    }

    private LocalDate toLocalDate(
            Date value
    ) {
        return value == null
                ? null
                : value.toLocalDate();
    }

    private LocalDateTime toLocalDateTime(
            Timestamp value
    ) {
        return value == null
                ? null
                : value.toLocalDateTime();
    }

    private record AdminAccount(
            Long userId,
            String role,
            String status,
            Long managedLibraryId,
            String managedLibraryCode
    ) {
    }

    private record LibraryAssignment(
            Long libraryId,
            String libCode,
            String libraryName
    ) {
    }
}