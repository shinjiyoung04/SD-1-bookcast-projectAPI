package com.example.teamproject1.libraryinfo.service;

import com.example.teamproject1.libraryinfo.dto.ManagedLibraryInfoResponse;
import com.example.teamproject1.libraryinfo.service.Data4LibraryManagedLibraryClient.ExternalLibraryInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminManagedLibraryInfoService {

    private static final Duration CACHE_DURATION = Duration.ofHours(6);

    private final JdbcTemplate jdbcTemplate;

    private final Data4LibraryManagedLibraryClient
            data4LibraryManagedLibraryClient;

    private final Map<String, CacheEntry> cache =
            new ConcurrentHashMap<>();

    public ManagedLibraryInfoResponse getManagedLibraryInfo(
            Long requesterUserId,
            boolean refresh
    ) {
        AdminScope scope = resolveAdminScope(requesterUserId);

        if (refresh) {
            cache.remove(scope.libraryCode());
        }

        ExternalLibraryInfo externalInfo = loadExternalInfo(
                scope.libraryCode()
        );

        return new ManagedLibraryInfoResponse(
                scope.libraryId(),
                firstNonBlank(
                        externalInfo.libraryCode(),
                        scope.libraryCode()
                ),
                firstNonBlank(
                        externalInfo.libraryName(),
                        scope.libraryName(),
                        "담당 도서관"
                ),
                externalInfo.address(),
                externalInfo.tel(),
                externalInfo.fax(),
                externalInfo.homepage(),
                externalInfo.closed(),
                externalInfo.operatingTime(),
                externalInfo.bookCount(),
                externalInfo.latitude(),
                externalInfo.longitude(),
                externalInfo.available(),
                externalInfo.message(),
                LocalDateTime.now()
        );
    }

    private ExternalLibraryInfo loadExternalInfo(String libraryCode) {
        CacheEntry cached = cache.get(libraryCode);

        if (
                cached != null
                        && Duration.between(
                        cached.savedAt(),
                        Instant.now()
                ).compareTo(CACHE_DURATION) < 0
        ) {
            return cached.value();
        }

        ExternalLibraryInfo loaded =
                data4LibraryManagedLibraryClient
                        .fetchLibraryInfo(libraryCode);

        cache.put(
                libraryCode,
                new CacheEntry(
                        loaded,
                        Instant.now()
                )
        );

        return loaded;
    }

    private AdminScope resolveAdminScope(Long requesterUserId) {
        if (requesterUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        List<AdminScope> scopes = jdbcTemplate.query(
                """
                SELECT
                    user_account.user_id,
                    user_account.role,
                    user_account.managed_library_id,

                    COALESCE(
                        NULLIF(
                            user_account.managed_library_code,
                            ''
                        ),
                        library.lib_code
                    ) AS managed_library_code,

                    library.library_name

                FROM users user_account

                LEFT JOIN libraries library
                  ON library.library_id =
                     user_account.managed_library_id

                WHERE
                    user_account.user_id = ?
                    AND user_account.status = 'ACTIVE'
                """,
                (resultSet, rowNumber) -> new AdminScope(
                        resultSet.getLong("user_id"),
                        normalizeRole(resultSet.getString("role")),
                        nullableLong(
                                resultSet.getObject("managed_library_id")
                        ),
                        resultSet.getString("managed_library_code"),
                        resultSet.getString("library_name")
                ),
                requesterUserId
        );

        if (scopes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "활성 관리자 계정을 찾을 수 없습니다."
            );
        }

        AdminScope scope = scopes.get(0);

        if (!"ADMIN".equals(scope.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "일반 도서관 관리자만 담당 도서관 정보를 조회할 수 있습니다."
            );
        }

        if (!StringUtils.hasText(scope.libraryCode())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "담당 도서관의 정보나루 도서관 코드가 필요합니다."
            );
        }

        return scope;
    }

    private Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "USER";
        }

        return role.trim()
                .toUpperCase()
                .replaceFirst("^ROLE_", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private record AdminScope(
            Long userId,
            String role,
            Long libraryId,
            String libraryCode,
            String libraryName
    ) {
    }

    private record CacheEntry(
            ExternalLibraryInfo value,
            Instant savedAt
    ) {
    }
}
