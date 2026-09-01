package com.example.teamproject1.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class ManagedLibrarySyncService {

    private final JdbcTemplate jdbcTemplate;

    public ManagedLibrary syncLibrary(
            String libraryCode,
            String libraryName,
            String address,
            String phone
    ) {
        String normalizedCode =
                normalizeRequired(
                        libraryCode,
                        "도서관 코드"
                );

        String normalizedName =
                normalizeRequired(
                        libraryName,
                        "도서관명"
                );

        String normalizedAddress =
                normalizeOptional(address);

        String normalizedPhone =
                normalizeOptional(phone);

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO libraries (
                        lib_code,
                        library_name,
                        address,
                        phone
                    )
                    VALUES (?, ?, ?, ?)

                    ON DUPLICATE KEY UPDATE
                        library_name =
                            VALUES(library_name),

                        address =
                            CASE
                                WHEN VALUES(address) IS NULL
                                  OR VALUES(address) = ''
                                THEN address
                                ELSE VALUES(address)
                            END,

                        phone =
                            CASE
                                WHEN VALUES(phone) IS NULL
                                  OR VALUES(phone) = ''
                                THEN phone
                                ELSE VALUES(phone)
                            END
                    """,
                    normalizedCode,
                    normalizedName,
                    normalizedAddress,
                    normalizedPhone
            );

            ManagedLibrary library =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT
                                library_id,
                                lib_code,
                                library_name,
                                address,
                                phone
                            FROM libraries
                            WHERE lib_code = ?
                            """,
                            (resultSet, rowNumber) ->
                                    new ManagedLibrary(
                                            resultSet.getLong(
                                                    "library_id"
                                            ),
                                            resultSet.getString(
                                                    "lib_code"
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
                                    ),
                            normalizedCode
                    );

            if (library == null
                    || library.libraryId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "담당 도서관 저장에 실패했습니다."
                );
            }

            return library;

        } catch (
                EmptyResultDataAccessException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "저장한 담당 도서관을 다시 찾지 못했습니다.",
                    exception
            );

        } catch (
                ResponseStatusException exception
        ) {
            throw exception;

        } catch (
                DataAccessException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "담당 도서관 저장 중 DB 오류가 발생했습니다.",
                    exception
            );
        }
    }

    public void assignLibraryToUser(
            Long userId,
            ManagedLibrary library
    ) {
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "회원 번호가 필요합니다."
            );
        }

        if (library == null
                || library.libraryId() == null
                || !StringUtils.hasText(
                library.libraryCode()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "담당 도서관 정보가 올바르지 않습니다."
            );
        }

        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE users
                        SET
                            managed_library_id = ?,
                            managed_library_code = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ?
                          AND status = 'ACTIVE'
                        """,
                        library.libraryId(),
                        library.libraryCode(),
                        userId
                );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "담당 도서관을 지정할 회원을 찾을 수 없습니다."
            );
        }
    }

    public void clearLibraryFromUser(
            Long userId
    ) {
        if (userId == null) {
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE users
                SET
                    managed_library_id = NULL,
                    managed_library_code = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """,
                userId
        );
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

    private String normalizeOptional(
            String value
    ) {
        return StringUtils.hasText(value)
                ? value.trim()
                : "";
    }

    public record ManagedLibrary(
            Long libraryId,
            String libraryCode,
            String libraryName,
            String address,
            String phone
    ) {
    }
}