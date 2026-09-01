package com.example.teamproject1.bookanalysis.service;

import com.example.teamproject1.book.dto.ExternalBookExistResponse;
import com.example.teamproject1.book.dto.ExternalBookResponse;
import com.example.teamproject1.book.service.Data4LibraryService;
import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse;
import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse.KeywordItemResponse;
import com.example.teamproject1.bookanalysis.dto.BookUsageAnalysisResponse.LoanTrendItemResponse;
import com.example.teamproject1.bookanalysis.dto.PurchaseEvidenceResponse;
import com.example.teamproject1.bookanalysis.dto.PurchaseEvidenceResponse.BookInfoResponse;
import com.example.teamproject1.bookanalysis.dto.PurchaseEvidenceResponse.CategoryPopularBookResponse;
import com.example.teamproject1.bookanalysis.dto.PurchaseEvidenceResponse.FreshnessEvidenceResponse;
import com.example.teamproject1.bookanalysis.dto.PurchaseEvidenceResponse.HoldingEvidenceResponse;
import com.example.teamproject1.bookanalysis.dto.PurchaseEvidenceResponse.LibraryCategoryDemandResponse;
import com.example.teamproject1.bookanalysis.dto.PurchaseEvidenceResponse.LibraryInfoResponse;
import com.example.teamproject1.bookanalysis.dto.PurchaseEvidenceResponse.LocalCitizenDemandResponse;
import com.example.teamproject1.bookanalysis.dto.PurchaseEvidenceResponse.NationalDemandResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookPurchaseEvidenceService {

    private static final Set<String> ADMIN_ROLES =
            Set.of(
                    "ADMIN",
                    "MASTER_ADMIN"
            );

    private static final int POPULAR_BOOK_PAGE_SIZE =
            200;

    private static final int TOP_BOOK_DISPLAY_SIZE =
            10;

    @Value("${data4library.base-url}")
    private String baseUrl;

    @Value("${data4library.auth-key}")
    private String authKey;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Data4LibraryService data4LibraryService;
    private final Data4LibraryUsageAnalysisService usageAnalysisService;

    private final RestClient restClient =
            RestClient.create();

    /**
     * 사서용 도서 구매 판단 근거 조회
     *
     * GET /api/admin/books/{isbn}/purchase-evidence
     *     ?requesterUserId=1
     */
    public PurchaseEvidenceResponse getPurchaseEvidence(
            Long requesterUserId,
            String isbn
    ) {
        String cleanIsbn =
                normalizeIsbn(isbn);

        AdminLibrary adminLibrary =
                requireAdminLibrary(
                        requesterUserId
                );

        ExternalBookResponse externalBook =
                findExternalBook(
                        cleanIsbn
                );

        BookInfoResponse bookInfo =
                toBookInfo(
                        externalBook
                );

        HoldingEvidenceResponse holding =
                getHoldingEvidence(
                        adminLibrary,
                        cleanIsbn
                );

        LocalCitizenDemandResponse localCitizenDemand =
                getLocalCitizenDemand(
                        adminLibrary.libraryId(),
                        cleanIsbn
                );

        LibraryCategoryDemandResponse libraryCategoryDemand =
                getLibraryCategoryDemand(
                        adminLibrary,
                        bookInfo
                );

        NationalDemandResponse nationalDemand =
                getNationalDemand(
                        cleanIsbn
                );

        FreshnessEvidenceResponse freshness =
                getFreshnessEvidence(
                        bookInfo.publicationYear()
                );

        List<String> evidenceSummary =
                buildEvidenceSummary(
                        adminLibrary,
                        holding,
                        localCitizenDemand,
                        libraryCategoryDemand,
                        nationalDemand,
                        freshness
                );

        return new PurchaseEvidenceResponse(
                cleanIsbn,

                new LibraryInfoResponse(
                        adminLibrary.libraryId(),
                        adminLibrary.libCode(),
                        adminLibrary.libraryName()
                ),

                bookInfo,
                holding,
                localCitizenDemand,
                libraryCategoryDemand,
                nationalDemand,
                freshness,
                evidenceSummary
        );
    }

    /**
     * 로그인 사용자가 관리자이고 담당 도서관이 지정되어 있는지 확인합니다.
     */
    private AdminLibrary requireAdminLibrary(
            Long requesterUserId
    ) {
        if (requesterUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        try {
            AdminLibrary adminLibrary =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT
                                u.user_id,
                                u.role,
                                COALESCE(
                                    u.managed_library_id,
                                    code_library.library_id
                                ) AS managed_library_id,

                                COALESCE(
                                    u.managed_library_code,
                                    id_library.lib_code,
                                    code_library.lib_code
                                ) AS managed_library_code,

                                COALESCE(
                                    id_library.library_name,
                                    code_library.library_name
                                ) AS managed_library_name

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
                                    new AdminLibrary(
                                            resultSet.getLong(
                                                    "user_id"
                                            ),
                                            normalizeRole(
                                                    resultSet.getString(
                                                            "role"
                                                    )
                                            ),
                                            resultSet.getObject(
                                                    "managed_library_id",
                                                    Long.class
                                            ),
                                            normalizeNullable(
                                                    resultSet.getString(
                                                            "managed_library_code"
                                                    )
                                            ),
                                            normalizeNullable(
                                                    resultSet.getString(
                                                            "managed_library_name"
                                                    )
                                            )
                                    ),
                            requesterUserId
                    );

            if (adminLibrary == null
                    || !ADMIN_ROLES.contains(
                    adminLibrary.role()
            )) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "관리자만 구매 판단 근거를 조회할 수 있습니다."
                );
            }

            if (adminLibrary.libraryId() == null
                    || !StringUtils.hasText(
                    adminLibrary.libCode()
            )) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "담당 도서관 또는 정보나루 도서관 코드가 지정되어 있지 않습니다."
                );
            }

            return adminLibrary;

        } catch (
                EmptyResultDataAccessException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "활성 상태의 관리자 계정을 찾을 수 없습니다."
            );
        }
    }

    private ExternalBookResponse findExternalBook(
            String cleanIsbn
    ) {
        try {
            List<ExternalBookResponse> books =
                    data4LibraryService.searchBooks(
                            null,
                            null,
                            null,
                            cleanIsbn,
                            null,
                            1,
                            20
                    );

            if (books == null
                    || books.isEmpty()) {
                log.warn(
                        "[구매 근거] 정보나루 도서 기본정보 없음. isbn={}",
                        cleanIsbn
                );

                return null;
            }

            return books.stream()
                    .filter(
                            book ->
                                    cleanIsbn.equals(
                                            normalizeNullableIsbn(
                                                    book.getIsbn13()
                                            )
                                    )
                    )
                    .findFirst()
                    .orElse(
                            books.get(0)
                    );

        } catch (Exception exception) {
            log.warn(
                    "[구매 근거] 정보나루 도서 기본정보 조회 실패. isbn={}, message={}",
                    cleanIsbn,
                    exception.getMessage()
            );

            return null;
        }
    }

    private BookInfoResponse toBookInfo(
            ExternalBookResponse book
    ) {
        if (book == null) {
            return new BookInfoResponse(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return new BookInfoResponse(
                normalizeNullable(
                        book.getTitle()
                ),
                normalizeNullable(
                        book.getAuthor()
                ),
                normalizeNullable(
                        book.getPublisher()
                ),
                normalizeNullable(
                        book.getPublicationYear()
                ),
                normalizeNullable(
                        book.getClassNo()
                ),
                normalizeNullable(
                        book.getClassName()
                ),
                normalizeNullable(
                        book.getImageUrl()
                )
        );
    }

    // 정보나루 bookExist API를 사용하여 담당 도서관의 소장 여부를 조회/
    private HoldingEvidenceResponse getHoldingEvidence(
            AdminLibrary adminLibrary,
            String cleanIsbn
    ) {
        try {
            ExternalBookExistResponse result =
                    data4LibraryService.checkBookExist(
                            adminLibrary.libCode(),
                            cleanIsbn
                    );

            boolean available =
                    result != null
                            && StringUtils.hasText(
                            result.getHasBook()
                    );

            return new HoldingEvidenceResponse(
                    available,
                    result != null
                            && Boolean.TRUE.equals(
                            result.getIsOwned()
                    ),
                    result == null
                            ? null
                            : result.getHasBook(),
                    result == null
                            ? "소장 여부 응답이 없습니다."
                            : result.getMessage()
            );

        } catch (Exception exception) {
            log.warn(
                    "[구매 근거] 소장 여부 조회 실패. libCode={}, isbn={}, message={}",
                    adminLibrary.libCode(),
                    cleanIsbn,
                    exception.getMessage()
            );

            return new HoldingEvidenceResponse(
                    false,
                    false,
                    null,
                    "정보나루 소장 여부 데이터를 불러오지 못했습니다."
            );
        }
    }

    // 프로젝트 DB에서 해당 도서관의 미처리 희망도서 신청 수와 활성 시민투표 수를 집계
    private LocalCitizenDemandResponse getLocalCitizenDemand(
            Long libraryId,
            String cleanIsbn
    ) {
        try {
            LocalCitizenDemandResponse result =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT
                                COUNT(
                                    DISTINCT a.application_id
                                ) AS pending_application_count,

                                COUNT(
                                    vote.application_id
                                ) AS active_vote_count,

                                MAX(
                                    a.created_at
                                ) AS latest_application_at

                            FROM hope_applications a

                            LEFT JOIN hope_votes vote
                              ON vote.application_id =
                                 a.application_id
                             AND vote.active =
                                 TRUE

                            JOIN users applicant
                              ON applicant.user_id =
                                 a.user_id
                             AND applicant.status =
                                 'ACTIVE'

                            WHERE a.library_id = ?
                              AND REPLACE(
                                    REPLACE(
                                        UPPER(
                                            COALESCE(
                                                a.isbn,
                                                ''
                                            )
                                        ),
                                        '-',
                                        ''
                                    ),
                                    ' ',
                                    ''
                                  ) = ?
                              AND a.status =
                                  'PENDING'
                              AND a.is_hidden =
                                  FALSE
                            """,
                            (resultSet, rowNumber) -> {
                                Timestamp latest =
                                        resultSet.getTimestamp(
                                                "latest_application_at"
                                        );

                                return new LocalCitizenDemandResponse(
                                        true,
                                        resultSet.getLong(
                                                "pending_application_count"
                                        ),
                                        resultSet.getLong(
                                                "active_vote_count"
                                        ),
                                        latest == null
                                                ? null
                                                : latest.toLocalDateTime(),
                                        "프로젝트 DB의 미처리 희망도서 신청과 활성 시민투표 기준입니다."
                                );
                            },
                            libraryId,
                            cleanIsbn.toUpperCase(
                                    Locale.ROOT
                            )
                    );

            if (result == null) {
                return new LocalCitizenDemandResponse(
                        true,
                        0L,
                        0L,
                        null,
                        "해당 ISBN의 미처리 희망도서 신청이 없습니다."
                );
            }

            return result;

        } catch (Exception exception) {
            log.warn(
                    "[구매 근거] 지역 시민 수요 집계 실패. libraryId={}, isbn={}, message={}",
                    libraryId,
                    cleanIsbn,
                    exception.getMessage()
            );

            return new LocalCitizenDemandResponse(
                    false,
                    null,
                    null,
                    null,
                    "프로젝트 DB에서 희망도서 신청·투표 수치를 집계하지 못했습니다. hope_applications와 hope_votes 테이블 구조를 확인해주세요."
            );
        }
    }

    /**
     * 정보나루 loanItemSrchByLib API를 호출하여
     * 담당 도서관의 최근 12개월 인기대출 상위 200권 중
     * 현재 도서와 같은 KDC 대주제의 비중을 계산
     */
    private LibraryCategoryDemandResponse getLibraryCategoryDemand(
            AdminLibrary adminLibrary,
            BookInfoResponse bookInfo
    ) {
        String kdcMajor =
                extractKdcMajor(
                        bookInfo.classNo()
                );

        LocalDate endDate =
                LocalDate.now();

        LocalDate startDate =
                endDate.minusYears(1)
                        .plusDays(1);

        if (!StringUtils.hasText(
                kdcMajor
        )) {
            return new LibraryCategoryDemandResponse(
                    false,
                    bookInfo.classNo(),
                    bookInfo.className(),
                    startDate,
                    endDate,
                    0,
                    0,
                    null,
                    "데이터 부족",
                    List.of(),
                    "도서의 KDC 분류번호가 없어 관내 동일 분야 수요를 계산할 수 없습니다."
            );
        }

        try {
            URI uri =
                    UriComponentsBuilder
                            .fromUriString(
                                    baseUrl
                                            + "/loanItemSrchByLib"
                            )
                            .queryParam(
                                    "authKey",
                                    authKey
                            )
                            .queryParam(
                                    "libCode",
                                    adminLibrary.libCode()
                            )
                            .queryParam(
                                    "startDt",
                                    startDate
                            )
                            .queryParam(
                                    "endDt",
                                    endDate
                            )
                            .queryParam(
                                    "pageNo",
                                    1
                            )
                            .queryParam(
                                    "pageSize",
                                    POPULAR_BOOK_PAGE_SIZE
                            )
                            .queryParam(
                                    "format",
                                    "json"
                            )
                            .build()
                            .encode()
                            .toUri();

            log.info(
                    "[정보나루 관내 인기대출 조회] libCode={}, isbnKdc={}, uri={}",
                    adminLibrary.libCode(),
                    kdcMajor,
                    maskAuthKey(
                            uri.toString()
                    )
            );

            String responseBody =
                    restClient
                            .get()
                            .uri(uri)
                            .retrieve()
                            .body(
                                    String.class
                            );

            if (!StringUtils.hasText(
                    responseBody
            )) {
                throw new IllegalStateException(
                        "정보나루 관내 인기대출 응답이 비어 있습니다."
                );
            }

            logResponsePreview(
                    "정보나루 관내 인기대출 응답",
                    responseBody
            );

            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            JsonNode responseNode =
                    root.path(
                            "response"
                    );

            validateApiError(
                    responseNode
            );

            List<JsonNode> rawBooks =
                    collectDocumentItems(
                            responseNode.path(
                                    "docs"
                            )
                    );

            List<CategoryPopularBookResponse>
                    sameCategoryBooks =
                    new ArrayList<>();

            int sameCategoryBookCount =
                    0;

            for (JsonNode item : rawBooks) {
                String itemClassNo =
                        firstText(
                                item,
                                "class_no",
                                "classNo"
                        );

                String itemKdcMajor =
                        extractKdcMajor(
                                itemClassNo
                        );

                if (!kdcMajor.equals(
                        itemKdcMajor
                )) {
                    continue;
                }

                sameCategoryBookCount++;

                sameCategoryBooks.add(
                        new CategoryPopularBookResponse(
                                firstInteger(
                                        item,
                                        "ranking",
                                        "rank"
                                ),
                                firstText(
                                        item,
                                        "bookname",
                                        "title"
                                ),
                                firstText(
                                        item,
                                        "authors",
                                        "author"
                                ),
                                firstText(
                                        item,
                                        "publisher"
                                ),
                                firstText(
                                        item,
                                        "isbn13",
                                        "isbn"
                                ),
                                itemClassNo,
                                firstText(
                                        item,
                                        "class_nm",
                                        "className"
                                ),
                                null
                        )
                );
            }

            sameCategoryBooks.sort(
                    Comparator.comparing(
                            CategoryPopularBookResponse::ranking,
                            Comparator.nullsLast(
                                    Integer::compareTo
                            )
                    )
            );

            List<CategoryPopularBookResponse>
                    topBooks =
                    sameCategoryBooks.size()
                            > TOP_BOOK_DISPLAY_SIZE
                            ? List.copyOf(
                            sameCategoryBooks.subList(
                                    0,
                                    TOP_BOOK_DISPLAY_SIZE
                            )
                    )
                            : List.copyOf(
                            sameCategoryBooks
                    );

            int returnedBookCount =
                    rawBooks.size();

            String demandLevel =
                    calculateCategoryDemandLevel(
                            returnedBookCount,
                            sameCategoryBookCount
                    );

            String message;

            if (returnedBookCount == 0) {
                message =
                        "최근 12개월 관내 인기대출 데이터가 제공되지 않았습니다.";
            } else {
                message =
                        "최근 12개월 관내 인기대출 상위 "
                                + returnedBookCount
                                + "권 중 동일 KDC 분야 도서는 "
                                + sameCategoryBookCount
                                + "권입니다. 정보나루의 libCode 기준 조회는 대출건수가 아니라 대출순위만 제공합니다.";
            }

            return new LibraryCategoryDemandResponse(
                    true,
                    bookInfo.classNo(),
                    bookInfo.className(),
                    startDate,
                    endDate,
                    returnedBookCount,
                    sameCategoryBookCount,
                    null,
                    demandLevel,
                    topBooks,
                    message
            );

        } catch (Exception exception) {
            log.warn(
                    "[구매 근거] 관내 동일 분야 수요 조회 실패. libCode={}, kdc={}, message={}",
                    adminLibrary.libCode(),
                    kdcMajor,
                    exception.getMessage()
            );

            return new LibraryCategoryDemandResponse(
                    false,
                    bookInfo.classNo(),
                    bookInfo.className(),
                    startDate,
                    endDate,
                    0,
                    0,
                    null,
                    "데이터 부족",
                    List.of(),
                    "정보나루 관내 인기대출 데이터를 불러오지 못했습니다."
            );
        }
    }

    // 이미 구현된 usageAnalysisList 서비스 사용하여 전국 누적 대출, 최근 12개월 추이, 이용자층, 키워드를 가져오기

    private NationalDemandResponse getNationalDemand(
            String cleanIsbn
    ) {
        Integer totalLoanCount =
                null;

        String trendStatus =
                "데이터 부족";

        List<LoanTrendItemResponse> loanTrend =
                List.of();

        List<BookUsageAnalysisResponse.PopularGroupItemResponse>
                popularGroups =
                List.of();

        List<KeywordItemResponse> usageAnalysisKeywords =
                List.of();

        boolean usageAvailable =
                false;

        String usageMessage =
                "정보나루 전국 이용분석 데이터가 제공되지 않았습니다.";

        /*
         * usageAnalysisList:
         * - 전국 누적 대출
         * - 최근 12개월 대출 추이
         * - 최근 30일 주요 이용자층
         * - 도서 키워드
         */
        try {
            BookUsageAnalysisResponse usage =
                    usageAnalysisService
                            .getUsageAnalysis(
                                    cleanIsbn
                            );

            totalLoanCount =
                    usage.totalLoanCount();

            loanTrend =
                    usage.loanTrend() == null
                            ? List.of()
                            : usage.loanTrend();

            popularGroups =
                    usage.popularGroups() == null
                            ? List.of()
                            : usage.popularGroups();

            usageAnalysisKeywords =
                    usage.keywords() == null
                            ? List.of()
                            : usage.keywords();

            usageAvailable =
                    (totalLoanCount != null
                            && totalLoanCount > 0)
                            || !loanTrend.isEmpty()
                            || !popularGroups.isEmpty();

            trendStatus =
                    calculateNationalTrendStatus(
                            loanTrend
                    );

            usageMessage =
                    usageAvailable
                            ? "정보나루 전국 공공도서관 도서별 이용분석 기준입니다."
                            : "정보나루 usageAnalysisList에 해당 ISBN의 이용 데이터가 없습니다.";

        } catch (Exception exception) {
            log.warn(
                    "[구매 근거] 전국 이용분석 조회 실패. isbn={}, message={}",
                    cleanIsbn,
                    exception.getMessage()
            );

            usageMessage =
                    "정보나루 usageAnalysisList에서 해당 ISBN의 이용 데이터를 조회하지 못했습니다.";
        }

        List<KeywordItemResponse> keywordListKeywords =
                usageAnalysisService
                        .getKeywords(
                                cleanIsbn
                        );

        List<KeywordItemResponse> mergedKeywords =
                mergeKeywords(
                        usageAnalysisKeywords,
                        keywordListKeywords
                );

        String message;

        if (usageAvailable
                && !mergedKeywords.isEmpty()) {
            message =
                    usageMessage
                            + " 키워드는 usageAnalysisList와 keywordList 결과를 합쳐 표시합니다.";
        } else if (usageAvailable) {
            message =
                    usageMessage
                            + " 키워드 데이터는 제공되지 않았습니다.";
        } else if (!mergedKeywords.isEmpty()) {
            message =
                    usageMessage
                            + " 다만 keywordList에서 핵심 키워드는 조회했습니다.";
        } else {
            message =
                    usageMessage
                            + " keywordList에도 해당 ISBN의 키워드가 없습니다.";
        }

        return new NationalDemandResponse(
                usageAvailable,
                totalLoanCount,
                trendStatus,
                loanTrend,
                popularGroups,
                mergedKeywords,
                message
        );
    }

    private List<KeywordItemResponse> mergeKeywords(
            List<KeywordItemResponse> first,
            List<KeywordItemResponse> second
    ) {
        java.util.Map<String, KeywordItemResponse> merged =
                new java.util.LinkedHashMap<>();

        List<KeywordItemResponse> all =
                new ArrayList<>();

        if (first != null) {
            all.addAll(first);
        }

        if (second != null) {
            all.addAll(second);
        }

        for (KeywordItemResponse item : all) {
            if (item == null
                    || !StringUtils.hasText(
                    item.word()
            )) {
                continue;
            }

            String key =
                    item.word()
                            .trim()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            KeywordItemResponse previous =
                    merged.get(key);

            if (previous == null) {
                merged.put(
                        key,
                        new KeywordItemResponse(
                                item.word().trim(),
                                item.weight()
                        )
                );

                continue;
            }

            Double previousWeight =
                    previous.weight();

            Double currentWeight =
                    item.weight();

            if (previousWeight == null
                    || (currentWeight != null
                    && currentWeight > previousWeight)) {
                merged.put(
                        key,
                        new KeywordItemResponse(
                                item.word().trim(),
                                currentWeight
                        )
                );
            }
        }

        List<KeywordItemResponse> result =
                new ArrayList<>(
                        merged.values()
                );

        result.sort(
                Comparator.comparing(
                                KeywordItemResponse::weight,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                        .thenComparing(
                                KeywordItemResponse::word
                        )
        );

        if (result.size() > 12) {
            return List.copyOf(
                    result.subList(
                            0,
                            12
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private FreshnessEvidenceResponse getFreshnessEvidence(
            String publicationYear
    ) {
        Integer parsedYear =
                parsePublicationYear(
                        publicationYear
                );

        if (parsedYear == null) {
            return new FreshnessEvidenceResponse(
                    publicationYear,
                    null,
                    "판단 불가"
            );
        }

        int yearsSincePublication =
                Math.max(
                        0,
                        Year.now().getValue()
                                - parsedYear
                );

        String freshnessLevel;

        if (yearsSincePublication <= 1) {
            freshnessLevel =
                    "신간";
        } else if (yearsSincePublication <= 3) {
            freshnessLevel =
                    "비교적 최신";
        } else {
            freshnessLevel =
                    "일반 자료";
        }

        return new FreshnessEvidenceResponse(
                publicationYear,
                yearsSincePublication,
                freshnessLevel
        );
    }

    private List<String> buildEvidenceSummary(
            AdminLibrary adminLibrary,
            HoldingEvidenceResponse holding,
            LocalCitizenDemandResponse localDemand,
            LibraryCategoryDemandResponse categoryDemand,
            NationalDemandResponse nationalDemand,
            FreshnessEvidenceResponse freshness
    ) {
        List<String> result =
                new ArrayList<>();

        if (Boolean.TRUE.equals(
                holding.available()
        )) {
            if (Boolean.TRUE.equals(
                    holding.owned()
            )) {
                result.add(
                        adminLibrary.libraryName()
                                + "에 이미 소장된 도서입니다."
                );
            } else {
                result.add(
                        adminLibrary.libraryName()
                                + "에 소장되지 않은 도서입니다."
                );
            }
        }

        if (Boolean.TRUE.equals(
                localDemand.available()
        )) {
            result.add(
                    "현재 미처리 희망도서 신청 "
                            + localDemand.pendingApplicationCount()
                            + "건, 활성 시민투표 "
                            + localDemand.activeVoteCount()
                            + "표가 확인되었습니다."
            );
        } else {
            result.add(
                    localDemand.message()
            );
        }

        if (Boolean.TRUE.equals(
                categoryDemand.available()
        )
                && categoryDemand.returnedBookCount() != null
                && categoryDemand.returnedBookCount() > 0) {
            double percentage =
                    categoryDemand.sameCategoryBookCount()
                            * 100.0
                            / categoryDemand.returnedBookCount();

            result.add(
                    "최근 12개월 관내 인기대출 상위 "
                            + categoryDemand.returnedBookCount()
                            + "권 중 동일 KDC 분야가 "
                            + categoryDemand.sameCategoryBookCount()
                            + "권("
                            + String.format(
                            Locale.KOREA,
                            "%.1f",
                            percentage
                    )
                            + "%)입니다."
            );
        }

        if (Boolean.TRUE.equals(
                nationalDemand.available()
        )) {
            result.add(
                    "전국 공공도서관 누적 대출은 "
                            + defaultZero(
                            nationalDemand.totalLoanCount()
                    )
                            + "건이며 최근 추이는 "
                            + nationalDemand.trendStatus()
                            + "입니다."
            );
        }

        if (StringUtils.hasText(
                freshness.freshnessLevel()
        )
                && !"판단 불가".equals(
                freshness.freshnessLevel()
        )) {
            result.add(
                    "출판연도 기준 자료 최신성은 "
                            + freshness.freshnessLevel()
                            + "입니다."
            );
        }

        return List.copyOf(
                result
        );
    }

    /**
     * 관내 인기대출 상위목록에서 동일 KDC 대주제가 차지하는 비율
     *
     * 20% 이상: 높음
     * 10% 이상 20% 미만: 보통
     * 10% 미만: 낮음
     */
    private String calculateCategoryDemandLevel(
            int returnedBookCount,
            int sameCategoryBookCount
    ) {
        if (returnedBookCount <= 0) {
            return "데이터 부족";
        }

        double ratio =
                (double) sameCategoryBookCount
                        / returnedBookCount;

        if (ratio >= 0.20) {
            return "높음";
        }

        if (ratio >= 0.10) {
            return "보통";
        }

        return "낮음";
    }

    // 최근 3개월이 연속 증가/감소하는지 확인
    private String calculateNationalTrendStatus(
            List<LoanTrendItemResponse> loanTrend
    ) {
        if (loanTrend == null
                || loanTrend.size() < 3) {
            return "데이터 부족";
        }

        List<LoanTrendItemResponse> sorted =
                new ArrayList<>(
                        loanTrend
                );

        sorted.sort(
                Comparator.comparing(
                        LoanTrendItemResponse::loanMonth,
                        Comparator.nullsLast(
                                String::compareTo
                        )
                )
        );

        int size =
                sorted.size();

        int first =
                defaultZero(
                        sorted.get(
                                size - 3
                        ).loanCount()
                );

        int second =
                defaultZero(
                        sorted.get(
                                size - 2
                        ).loanCount()
                );

        int third =
                defaultZero(
                        sorted.get(
                                size - 1
                        ).loanCount()
                );

        if (first < second
                && second < third) {
            return "증가";
        }

        if (first > second
                && second > third) {
            return "감소";
        }

        return "유지";
    }

    private List<JsonNode> collectDocumentItems(
            JsonNode node
    ) {
        List<JsonNode> result =
                new ArrayList<>();

        collectDocumentItems(
                node,
                result
        );

        return result;
    }

    private void collectDocumentItems(
            JsonNode node,
            List<JsonNode> result
    ) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                collectDocumentItems(
                        child,
                        result
                );
            }

            return;
        }

        if (!node.isObject()) {
            return;
        }

        JsonNode wrappedDoc =
                node.get(
                        "doc"
                );

        if (wrappedDoc != null
                && !wrappedDoc.isNull()) {
            collectDocumentItems(
                    wrappedDoc,
                    result
            );

            return;
        }

        if (StringUtils.hasText(
                firstText(
                        node,
                        "bookname",
                        "title",
                        "isbn13",
                        "isbn"
                )
        )) {
            result.add(node);
        }
    }

    private void validateApiError(
            JsonNode responseNode
    ) {
        if (responseNode == null
                || responseNode.isMissingNode()
                || responseNode.isNull()) {
            throw new IllegalStateException(
                    "정보나루 응답의 response 노드가 없습니다."
            );
        }

        String errCode =
                firstText(
                        responseNode,
                        "errCode"
                );

        String error =
                firstText(
                        responseNode,
                        "error",
                        "errMsg",
                        "message"
                );

        if (StringUtils.hasText(
                errCode
        ) || StringUtils.hasText(
                error
        )) {
            throw new IllegalStateException(
                    StringUtils.hasText(
                            error
                    )
                            ? error
                            : "정보나루 API 오류 코드: "
                            + errCode
            );
        }
    }

    private String extractKdcMajor(
            String classNo
    ) {
        if (!StringUtils.hasText(
                classNo
        )) {
            return null;
        }

        String normalized =
                classNo.trim()
                        .replaceAll(
                                "[^0-9]",
                                ""
                        );

        if (!StringUtils.hasText(
                normalized
        )) {
            return null;
        }

        return normalized.substring(
                0,
                1
        );
    }

    private Integer parsePublicationYear(
            String publicationYear
    ) {
        if (!StringUtils.hasText(
                publicationYear
        )) {
            return null;
        }

        String normalized =
                publicationYear.replaceAll(
                        "[^0-9]",
                        ""
                );

        if (normalized.length() < 4) {
            return null;
        }

        try {
            int year =
                    Integer.parseInt(
                            normalized.substring(
                                    0,
                                    4
                            )
                    );

            if (year < 1000
                    || year > Year.now()
                    .getValue()
                    + 1) {
                return null;
            }

            return year;

        } catch (
                NumberFormatException exception
        ) {
            return null;
        }
    }

    private String normalizeIsbn(
            String isbn
    ) {
        if (!StringUtils.hasText(
                isbn
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN이 필요합니다."
            );
        }

        String cleanIsbn =
                isbn.replaceAll(
                                "[^0-9Xx]",
                                ""
                        )
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (cleanIsbn.length() != 10
                && cleanIsbn.length() != 13) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ISBN은 10자리 또는 13자리여야 합니다."
            );
        }

        return cleanIsbn;
    }

    private String normalizeNullableIsbn(
            String isbn
    ) {
        if (!StringUtils.hasText(
                isbn
        )) {
            return null;
        }

        return isbn.replaceAll(
                        "[^0-9Xx]",
                        ""
                )
                .toUpperCase(
                        Locale.ROOT
                );
    }

    private String normalizeRole(
            String role
    ) {
        return String.valueOf(
                        role
                )
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
        return StringUtils.hasText(
                value
        )
                ? value.trim()
                : null;
    }

    private String firstText(
            JsonNode node,
            String... fieldNames
    ) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()) {
            return null;
        }

        for (String fieldName : fieldNames) {
            JsonNode value =
                    node.get(
                            fieldName
                    );

            if (value == null
                    || value.isNull()) {
                continue;
            }

            String text =
                    value.asText();

            if (StringUtils.hasText(
                    text
            )) {
                return text.trim();
            }
        }

        return null;
    }

    private Integer firstInteger(
            JsonNode node,
            String... fieldNames
    ) {
        String value =
                firstText(
                        node,
                        fieldNames
                );

        if (!StringUtils.hasText(
                value
        )) {
            return null;
        }

        String normalized =
                value.replaceAll(
                        "[^0-9-]",
                        ""
                );

        if (!StringUtils.hasText(
                normalized
        )
                || "-".equals(
                normalized
        )) {
            return null;
        }

        try {
            return Integer.valueOf(
                    normalized
            );
        } catch (
                NumberFormatException exception
        ) {
            return null;
        }
    }

    private int defaultZero(
            Integer value
    ) {
        return value == null
                ? 0
                : value;
    }

    private void logResponsePreview(
            String title,
            String responseBody
    ) {
        if (!StringUtils.hasText(
                responseBody
        )) {
            log.warn(
                    "[{}] 응답이 비어 있습니다.",
                    title
            );

            return;
        }

        int previewLength =
                Math.min(
                        responseBody.length(),
                        3000
                );

        log.info(
                "[{} 앞부분] {}",
                title,
                responseBody.substring(
                        0,
                        previewLength
                )
        );
    }

    private String maskAuthKey(
            String uri
    ) {
        if (!StringUtils.hasText(
                uri
        )
                || !StringUtils.hasText(
                authKey
        )) {
            return uri;
        }

        return uri.replace(
                authKey,
                "****"
        );
    }

    private record AdminLibrary(
            Long userId,
            String role,
            Long libraryId,
            String libCode,
            String libraryName
    ) {
    }
}

