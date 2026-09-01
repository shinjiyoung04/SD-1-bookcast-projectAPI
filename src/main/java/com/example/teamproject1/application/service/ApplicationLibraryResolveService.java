package com.example.teamproject1.application.service;

import com.example.teamproject1.application.dto.ApplicationLibraryResolveRequest;
import com.example.teamproject1.application.dto.ApplicationLibraryResolveResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationLibraryResolveService {

    private final JdbcTemplate jdbcTemplate;

    // 정보나루 정보와 내부 librarycode 연결
    @Transactional
    public ApplicationLibraryResolveResponse resolve(
            ApplicationLibraryResolveRequest request
    ) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "도서관 정보가 필요합니다."
            );
        }

        String libCode =
                normalizeRequired(
                        request.libraryCode(),
                        "도서관 코드"
                );

        String libraryName =
                normalizeRequired(
                        request.libraryName(),
                        "도서관명"
                );

        String address =
                normalizeOptional(
                        request.address()
                );

        String phone =
                normalizeOptional(
                        request.phone()
                );

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
                    libCode,
                    libraryName,
                    address,
                    phone
            );

            ApplicationLibraryResolveResponse response =
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
                                    new ApplicationLibraryResolveResponse(
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
                            libCode
                    );

            if (response == null
                    || response.libraryId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "내부 도서관 번호를 생성하지 못했습니다."
                );
            }

            if (!StringUtils.hasText(
                    response.libraryCode()
            )) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "도서관 정보나루 코드 저장에 실패했습니다."
                );
            }

            log.info(
                    "[ApplicationLibraryResolveService] "
                            + "도서관 연결 완료. "
                            + "libraryId={}, libCode={}, libraryName={}",
                    response.libraryId(),
                    response.libraryCode(),
                    response.libraryName()
            );

            return response;

        } catch (
                EmptyResultDataAccessException exception
        ) {
            log.error(
                    "[ApplicationLibraryResolveService] "
                            + "등록한 도서관 조회 실패. libCode={}",
                    libCode,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "등록한 도서관 정보를 다시 찾지 못했습니다.",
                    exception
            );

        } catch (
                ResponseStatusException exception
        ) {
            throw exception;

        } catch (
                DataAccessException exception
        ) {
            log.error(
                    "[ApplicationLibraryResolveService] "
                            + "도서관 연결 DB 오류. "
                            + "libCode={}, libraryName={}",
                    libCode,
                    libraryName,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "신청 도서관 연결 중 DB 오류가 발생했습니다.",
                    exception
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

    private String normalizeOptional(
            String value
    ) {
        return StringUtils.hasText(value)
                ? value.trim()
                : "";
    }
}