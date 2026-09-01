package com.example.teamproject1.application.duplicate.service;

import com.example.teamproject1.application.duplicate.dto.ApplicationDuplicateCheckResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationDuplicateService {

    private final JdbcTemplate jdbcTemplate;

    // 희망도서 신청 중복 체크
    public ApplicationDuplicateCheckResponse checkDuplicate(
            Long userId,
            String isbn,
            Long libraryId,
            String libCode
    ) {
        validateActiveUser(userId);

        String normalizedIsbn = normalizeIsbn(isbn);
        String normalizedLibCode = normalizeNullable(libCode);

        if (libraryId == null && normalizedLibCode == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "도서관 번호 또는 도서관 코드가 필요합니다."
            );
        }

        String libraryCondition;
        List<Object> parameters = new ArrayList<>();

        if (libraryId != null && normalizedLibCode != null) {
            libraryCondition =
                    """
                    (
                        application.library_id = ?
                        OR library.lib_code = ?
                    )
                    """;

            parameters.add(libraryId);
            parameters.add(normalizedLibCode);
        } else if (libraryId != null) {
            libraryCondition = "application.library_id = ?";
            parameters.add(libraryId);
        } else {
            libraryCondition = "library.lib_code = ?";
            parameters.add(normalizedLibCode);
        }

        parameters.add(normalizedIsbn);
        parameters.add(userId);

        String sql =
                """
                SELECT
                    application.application_id,
                    application.user_id,
                    application.title,
                    application.library_id,
                    library.lib_code,
                    library.library_name,
                    application.status,
                    (
                        SELECT COUNT(*)
                        FROM hope_votes vote
                        WHERE vote.application_id = application.application_id
                          AND vote.active = TRUE
                    ) AS vote_count
                FROM hope_applications application
                JOIN libraries library
                  ON library.library_id = application.library_id
                WHERE
                """
                        + libraryCondition
                        + """
                  AND UPPER(
                      REPLACE(
                          REPLACE(application.isbn, '-', ''),
                          ' ',
                          ''
                      )
                  ) = ?
                  AND application.status = 'PENDING'
                  AND COALESCE(application.is_hidden, FALSE) = FALSE
                ORDER BY
                    CASE
                        WHEN application.user_id <> ? THEN 0
                        ELSE 1
                    END,
                    application.created_at ASC,
                    application.application_id ASC
                LIMIT 1
                """;

        try {
            List<ApplicationDuplicateCheckResponse> results =
                    jdbcTemplate.query(
                            sql,
                            (resultSet, rowNumber) -> {
                                Long applicationId =
                                        resultSet.getLong("application_id");

                                Long ownerUserId =
                                        resultSet.getLong("user_id");

                                boolean ownApplication =
                                        Objects.equals(ownerUserId, userId);

                                String message = ownApplication
                                        ? "이미 같은 도서를 같은 도서관에 신청했습니다. 나의 희망도서 신청 내역을 확인해주세요."
                                        : "다른 사용자가 같은 도서를 같은 도서관에 이미 신청했습니다. 중복 신청 대신 시민투표에서 '저도 원해요'에 참여해주세요.";

                                String redirectUrl = ownApplication
                                        ? "/member/mypage?tab=applications"
                                        : "/citizen-votes?applicationId="
                                                + applicationId;

                                return new ApplicationDuplicateCheckResponse(
                                        true,
                                        ownApplication,
                                        applicationId,
                                        resultSet.getString("title"),
                                        resultSet.getLong("library_id"),
                                        resultSet.getString("lib_code"),
                                        resultSet.getString("library_name"),
                                        resultSet.getString("status"),
                                        resultSet.getLong("vote_count"),
                                        message,
                                        redirectUrl
                                );
                            },
                            parameters.toArray()
                    );

            return results.isEmpty()
                    ? ApplicationDuplicateCheckResponse.notDuplicate()
                    : results.get(0);

        } catch (DataAccessException exception) {
            log.error(
                    "[ApplicationDuplicateService] 중복 희망도서 확인 실패. userId={}, isbn={}, libraryId={}, libCode={}",
                    userId,
                    normalizedIsbn,
                    libraryId,
                    normalizedLibCode,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "중복 희망도서 신청 확인 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private void validateActiveUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users
                WHERE user_id = ?
                  AND status = 'ACTIVE'
                """,
                Integer.class,
                userId
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "활성 상태의 로그인 사용자를 찾을 수 없습니다."
            );
        }
    }

    private String normalizeIsbn(String isbn) {
        if (!StringUtils.hasText(isbn)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN이 필요합니다."
            );
        }

        String normalized = isbn
                .replaceAll("[^0-9Xx]", "")
                .toUpperCase(Locale.ROOT)
                .trim();

        if (normalized.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN이 필요합니다."
            );
        }

        if (normalized.length() != 10 && normalized.length() != 13) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN은 10자리 또는 13자리여야 합니다."
            );
        }

        return normalized;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value)
                ? value.trim()
                : null;
    }
}
