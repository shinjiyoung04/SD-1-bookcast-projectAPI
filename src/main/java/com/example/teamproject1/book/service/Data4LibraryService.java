package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.ExternalBookExistResponse;
import com.example.teamproject1.book.dto.ExternalBookResponse;
import com.example.teamproject1.book.dto.ExternalLibraryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.teamproject1.data4library.Data4LibraryCachedClient;
import com.example.teamproject1.data4library.Data4LibraryUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class Data4LibraryService {

    private static final String DEFAULT_REGION_CODE = "31";
    private static final String DEFAULT_DTL_REGION_CODE = "31100";

    private static final List<String> GOYANG_DTL_REGION_CODES = List.of(
            "31101", // 고양시 덕양구
            "31103", // 고양시 일산동구
            "31104"  // 고양시 일산서구
    );

    private static final List<String> ALL_REGION_CODES = List.of(
            "11", // 서울
            "21", // 부산
            "22", // 대구
            "23", // 인천
            "24", // 광주
            "25", // 대전
            "26", // 울산
            "29", // 세종
            "31", // 경기
            "32", // 강원
            "33", // 충북
            "34", // 충남
            "35", // 전북
            "36", // 전남
            "37", // 경북
            "38", // 경남
            "39"  // 제주
    );

    @Value("${data4library.base-url}")
    private String baseUrl;

    @Value("${data4library.auth-key}")
    private String authKey;

    private final ObjectMapper objectMapper;
    private final Data4LibraryCachedClient data4LibraryClient;

    public Data4LibraryService(
            ObjectMapper objectMapper,
            Data4LibraryCachedClient data4LibraryClient
    ) {
        this.objectMapper = objectMapper;
        this.data4LibraryClient = data4LibraryClient;
    }

    // 지역별 도서관 목록 조회
    //
    // 기본값:
    // - region=31: 경기도
    // - dtlRegion=31100: 고양시 전체
    //
    // 정보나루는 고양시 상위 코드 31100을 하위 구 전체로
    // 자동 확장하지 않으므로 31101, 31103, 31104를 각각 조회한다.
    public List<ExternalLibraryResponse> searchLibraries(
            Integer pageNo,
            Integer pageSize,
            String region,
            String dtlRegion,
            String libCode
    ) {
        try {
            String effectiveRegion =
                    hasText(region)
                            ? region.trim()
                            : DEFAULT_REGION_CODE;

            String effectiveDtlRegion;

            if (DEFAULT_REGION_CODE.equals(effectiveRegion)) {
                effectiveDtlRegion =
                        hasText(dtlRegion)
                                ? dtlRegion.trim()
                                : DEFAULT_DTL_REGION_CODE;
            } else {
                effectiveDtlRegion =
                        hasText(dtlRegion)
                                ? dtlRegion.trim()
                                : null;
            }

            int effectivePageNo =
                    pageNo == null || pageNo < 1
                            ? 1
                            : pageNo;

            int effectivePageSize =
                    pageSize == null || pageSize < 1
                            ? 50
                            : Math.min(pageSize, 50);

            log.info(
                    "[정보나루 도서관 목록 조회 요청] region={}, dtlRegion={}, libCode={}, pageNo={}, pageSize={}",
                    effectiveRegion,
                    effectiveDtlRegion,
                    libCode,
                    effectivePageNo,
                    effectivePageSize
            );

            List<ExternalLibraryResponse> result = new ArrayList<>();

            if (
                    DEFAULT_REGION_CODE.equals(effectiveRegion)
                            && DEFAULT_DTL_REGION_CODE.equals(effectiveDtlRegion)
            ) {
                /*
                 * 화면의 31100은 프로젝트에서 사용하는
                 * '고양시 전체' 선택값이다.
                 *
                 * 정보나루에는 각 도서관이 실제 구 코드로 등록되어 있으므로
                 * 덕양구, 일산동구, 일산서구를 각각 조회해 합친다.
                 */
                for (String goyangDtlRegionCode : GOYANG_DTL_REGION_CODES) {
                    try {
                        log.info(
                                "[정보나루 고양시 전체 도서관 조회] dtlRegion={}",
                                goyangDtlRegionCode
                        );

                        result.addAll(
                                requestLibraries(
                                        effectivePageNo,
                                        effectivePageSize,
                                        DEFAULT_REGION_CODE,
                                        goyangDtlRegionCode,
                                        libCode
                                )
                        );
                    } catch (Exception districtError) {
                        // 한 구가 실패해도 다른 구의 도서관 목록은 유지한다.
                        log.warn(
                                "[정보나루 고양시 일부 지역 도서관 조회 실패] dtlRegion={}, message={}",
                                goyangDtlRegionCode,
                                districtError.getMessage()
                        );

                        if (isData4LibraryUnavailable(districtError)) {
                            break;
                        }
                    }
                }
            } else {
                result.addAll(
                        requestLibraries(
                                effectivePageNo,
                                effectivePageSize,
                                effectiveRegion,
                                effectiveDtlRegion,
                                libCode
                        )
                );
            }

            result = removeDuplicateLibraries(result);

            log.info(
                    "[정보나루 도서관 목록 최종 결과 수] region={}, dtlRegion={}, count={}",
                    effectiveRegion,
                    effectiveDtlRegion,
                    result.size()
            );

            return result;

        } catch (Exception e) {
            log.error("[정보나루 도서관 조회 실패]", e);

            throw new RuntimeException(
                    "정보나루 도서관 조회 API 호출 실패: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private List<ExternalLibraryResponse> requestLibraries(
            Integer pageNo,
            Integer pageSize,
            String region,
            String dtlRegion,
            String libCode
    ) throws Exception {
        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl + "/libSrch")
                .queryParam("authKey", authKey)
                .queryParam("format", "json")
                .queryParamIfPresent("region", optionalText(region))
                .queryParamIfPresent("dtl_region", optionalText(dtlRegion))
                .queryParamIfPresent("libCode", optionalText(libCode))
                .queryParam("pageNo", pageNo)
                .queryParam("pageSize", pageSize)
                .build()
                .encode()
                .toUri();

        log.info(
                "[정보나루 도서관 조회 URI] {}",
                maskAuthKey(uri.toString())
        );

        String responseBody = data4LibraryClient.get(uri);

        logResponsePreview(
                "정보나루 도서관 조회 응답",
                responseBody
        );

        List<ExternalLibraryResponse> result =
                parseLibraries(responseBody);

        log.info(
                "[정보나루 도서관 조회 결과 수] region={}, dtlRegion={}, count={}",
                region,
                dtlRegion,
                result.size()
        );

        return result;
    }

    // 도서 검색
    public List<ExternalBookResponse> searchBooks(
            String keyword,
            String title,
            String author,
            String isbn13,
            String publisher,
            Integer pageNo,
            Integer pageSize
    ) {
        try {
            log.info("[정보나루 도서 검색 요청] keyword={}, title={}, author={}, isbn13={}, publisher={}, pageNo={}, pageSize={}",
                    keyword, title, author, isbn13, publisher, pageNo, pageSize);

            boolean isSimpleKeywordSearch =
                    hasText(keyword)
                            && !hasText(title)
                            && !hasText(author)
                            && !hasText(isbn13)
                            && !hasText(publisher);

            List<ExternalBookResponse> result;

            if (isSimpleKeywordSearch) {
                result = new ArrayList<>();

                log.info("[정보나루 통합검색] 1차 title 검색: {}", keyword);
                result.addAll(requestSearchBooks(
                        null,
                        keyword,
                        null,
                        null,
                        null,
                        pageNo,
                        pageSize
                ));

                if (result.isEmpty()) {
                    log.info("[정보나루 통합검색] 2차 author 검색: {}", keyword);
                    result.addAll(requestSearchBooks(
                            null,
                            null,
                            keyword,
                            null,
                            null,
                            pageNo,
                            pageSize
                    ));
                }

                if (result.isEmpty()) {
                    log.info("[정보나루 통합검색] 3차 publisher 검색: {}", keyword);
                    result.addAll(requestSearchBooks(
                            null,
                            null,
                            null,
                            null,
                            keyword,
                            pageNo,
                            pageSize
                    ));
                }

                if (result.isEmpty()) {
                    log.info("[정보나루 통합검색] 4차 keyword 검색: {}", keyword);
                    result.addAll(requestSearchBooks(
                            keyword,
                            null,
                            null,
                            null,
                            null,
                            pageNo,
                            pageSize
                    ));
                }

                result = removeDuplicateBooks(result);

            } else {
                result = requestSearchBooks(
                        keyword,
                        title,
                        author,
                        isbn13,
                        publisher,
                        pageNo,
                        pageSize
                );

                result = removeDuplicateBooks(result);
            }

            log.info("[정보나루 도서 검색 최종 결과 수] {}", result.size());

            if (!result.isEmpty()) {
                log.info("[정보나루 도서 검색 첫 번째 결과] {}", result.get(0));
            }

            return result;

        } catch (Exception e) {
            log.error("[정보나루 도서 검색 실패]", e);
            throw new RuntimeException("정보나루 도서 검색 API 호출 실패: " + e.getMessage(), e);
        }
    }

    private List<ExternalBookResponse> requestSearchBooks(
            String keyword,
            String title,
            String author,
            String isbn13,
            String publisher,
            Integer pageNo,
            Integer pageSize
    ) throws Exception {

        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl + "/srchBooks")
                .queryParam("authKey", authKey)
                .queryParam("format", "json")
                .queryParam("sort", "loan")
                .queryParam("order", "desc")
                .queryParam("exactMatch", "false")
                .queryParamIfPresent("keyword", optionalText(keyword))
                .queryParamIfPresent("title", optionalText(title))
                .queryParamIfPresent("author", optionalText(author))
                .queryParamIfPresent("isbn13", optionalText(isbn13))
                .queryParamIfPresent("publisher", optionalText(publisher))
                .queryParamIfPresent("pageNo", Optional.ofNullable(pageNo))
                .queryParamIfPresent("pageSize", Optional.ofNullable(pageSize))
                .build()
                .encode()
                .toUri();

        log.info("[정보나루 도서 검색 URI] {}", maskAuthKey(uri.toString()));

        String responseBody = data4LibraryClient.get(uri);

        logResponsePreview("정보나루 도서 검색 응답", responseBody);

        if (responseBody == null || responseBody.isBlank()) {
            log.warn("[정보나루 도서 검색] 응답 본문이 비어 있습니다.");
            return new ArrayList<>();
        }

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode responseNode = root.path("response");

        if (responseNode.isMissingNode() || responseNode.isNull()) {
            log.warn("[정보나루 도서 검색] response 노드가 없습니다.");
            return new ArrayList<>();
        }

        String errCode = text(responseNode, "errCode");
        String error = text(responseNode, "error");

        if (hasText(errCode) || hasText(error)) {
            log.warn("[정보나루 도서 검색 API 에러] errCode={}, error={}", errCode, error);
            return new ArrayList<>();
        }

        log.info("[정보나루 도서 검색 numFound] {}", responseNode.path("numFound").asText());

        JsonNode docsNode = responseNode.path("docs");

        List<ExternalBookResponse> result = new ArrayList<>();

        if (docsNode.isMissingNode() || docsNode.isNull()) {
            log.warn("[정보나루 도서 검색] docs 노드가 없습니다.");
            return result;
        }

        addBooksFromNode(result, docsNode);

        log.info("[정보나루 도서 검색 파싱 결과 수] {}", result.size());

        return result;
    }

    // ISBN 기준 소장 도서관 조회
    //
    // 기본값:
    // - region=31: 경기도
    // - dtlRegion=31100: 고양시 전체
    //
    // 전국 조회는 region=ALL을 명시한 경우에만 수행한다.
    public List<ExternalLibraryResponse> searchLibrariesByBook(
            String isbn,
            String region,
            String dtlRegion,
            Integer pageNo,
            Integer pageSize
    ) {
        try {
            if (!hasText(isbn)) {
                throw new IllegalArgumentException("ISBN이 비어 있습니다.");
            }

            String cleanIsbn = isbn.replaceAll("[^0-9Xx]", "");

            String effectiveRegion =
                    hasText(region)
                            ? region.trim()
                            : DEFAULT_REGION_CODE;

            String effectiveDtlRegion;

            if ("ALL".equalsIgnoreCase(effectiveRegion)) {
                effectiveDtlRegion = null;
            } else if (DEFAULT_REGION_CODE.equals(effectiveRegion)) {
                effectiveDtlRegion =
                        hasText(dtlRegion)
                                ? dtlRegion.trim()
                                : DEFAULT_DTL_REGION_CODE;
            } else {
                effectiveDtlRegion =
                        hasText(dtlRegion)
                                ? dtlRegion.trim()
                                : null;
            }

            int effectivePageNo =
                    pageNo == null || pageNo < 1
                            ? 1
                            : pageNo;

            int effectivePageSize =
                    pageSize == null || pageSize < 1
                            ? 20
                            : Math.min(pageSize, 50);

            log.info(
                    "[정보나루 도서 소장 도서관 조회 요청] isbn={}, cleanIsbn={}, region={}, dtlRegion={}, pageNo={}, pageSize={}",
                    isbn,
                    cleanIsbn,
                    effectiveRegion,
                    effectiveDtlRegion,
                    effectivePageNo,
                    effectivePageSize
            );

            List<ExternalLibraryResponse> result = new ArrayList<>();

            if ("ALL".equalsIgnoreCase(effectiveRegion)) {
                for (String regionCode : ALL_REGION_CODES) {
                    try {
                        log.info(
                                "[정보나루 전국 소장 도서관 조회] region={}",
                                regionCode
                        );

                        result.addAll(
                                requestLibrariesByBook(
                                        cleanIsbn,
                                        regionCode,
                                        null,
                                        effectivePageNo,
                                        effectivePageSize
                                )
                        );
                    } catch (Exception regionError) {
                        // 한 지역이 실패해도 전국 조회 전체를 중단하지 않는다.
                        log.warn(
                                "[정보나루 전국 소장 도서관 일부 조회 실패] region={}, message={}",
                                regionCode,
                                regionError.getMessage()
                        );

                        if (isData4LibraryUnavailable(regionError)) {
                            break;
                        }
                    }
                }
            } else if (
                    DEFAULT_REGION_CODE.equals(effectiveRegion)
                            && DEFAULT_DTL_REGION_CODE.equals(effectiveDtlRegion)
            ) {
                /*
                 * 정보나루에서 고양시 상위 코드 31100이 빈 결과를 반환하는
                 * 경우가 있으므로 실제 3개 구 코드를 각각 조회한다.
                 */
                for (String goyangDtlRegionCode : GOYANG_DTL_REGION_CODES) {
                    try {
                        log.info(
                                "[정보나루 고양시 구별 소장 도서관 조회] dtlRegion={}",
                                goyangDtlRegionCode
                        );

                        result.addAll(
                                requestLibrariesByBook(
                                        cleanIsbn,
                                        DEFAULT_REGION_CODE,
                                        goyangDtlRegionCode,
                                        effectivePageNo,
                                        effectivePageSize
                                )
                        );
                    } catch (Exception districtError) {
                        // 한 구의 조회가 실패해도 다른 구 결과는 유지한다.
                        log.warn(
                                "[정보나루 고양시 구별 조회 일부 실패] dtlRegion={}, message={}",
                                goyangDtlRegionCode,
                                districtError.getMessage()
                        );

                        if (isData4LibraryUnavailable(districtError)) {
                            break;
                        }
                    }
                }
            } else {
                result.addAll(
                        requestLibrariesByBook(
                                cleanIsbn,
                                effectiveRegion,
                                effectiveDtlRegion,
                                effectivePageNo,
                                effectivePageSize
                        )
                );
            }

            result = removeDuplicateLibraries(result);

            log.info(
                    "[정보나루 도서 소장 도서관 최종 결과 수] region={}, dtlRegion={}, count={}",
                    effectiveRegion,
                    effectiveDtlRegion,
                    result.size()
            );

            return result;

        } catch (Exception e) {
            log.error("[정보나루 도서 소장 도서관 조회 실패]", e);

            throw new RuntimeException(
                    "정보나루 도서 소장 도서관 조회 API 호출 실패: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private List<ExternalLibraryResponse> requestLibrariesByBook(
            String isbn,
            String region,
            String dtlRegion,
            Integer pageNo,
            Integer pageSize
    ) throws Exception {

        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl + "/libSrchByBook")
                .queryParam("authKey", authKey)
                .queryParam("format", "json")
                .queryParam("isbn", isbn)
                .queryParam("region", region)
                .queryParamIfPresent("dtl_region", optionalText(dtlRegion))
                .queryParamIfPresent("pageNo", Optional.ofNullable(pageNo))
                .queryParamIfPresent("pageSize", Optional.ofNullable(pageSize))
                .build()
                .encode()
                .toUri();

        log.info("[정보나루 도서 소장 도서관 조회 URI] {}", maskAuthKey(uri.toString()));

        String responseBody = data4LibraryClient.get(uri);

        logResponsePreview("정보나루 도서 소장 도서관 조회 응답", responseBody);

        List<ExternalLibraryResponse> result = parseLibraries(responseBody);

        log.info("[정보나루 도서 소장 도서관 조회 결과 수] region={}, dtlRegion={}, count={}",
                region, dtlRegion, result.size());

        return result;
    }

    // 특정 도서관의 도서 소장/대출 가능 여부 확인
    public ExternalBookExistResponse checkBookExist(
            String libCode,
            String isbn13
    ) {
        try {
            String cleanIsbn = isbn13 != null
                    ? isbn13.replaceAll("[^0-9Xx]", "")
                    : null;

            if (!hasText(libCode)) {
                return new ExternalBookExistResponse(
                        null,
                        cleanIsbn,
                        null,
                        null,
                        false,
                        false,
                        "UNKNOWN",
                        false,
                        "도서관 코드가 없어 소장 여부를 조회하지 않았습니다."
                );
            }

            if (!hasText(cleanIsbn)) {
                return new ExternalBookExistResponse(
                        libCode,
                        null,
                        null,
                        null,
                        false,
                        false,
                        "UNKNOWN",
                        false,
                        "ISBN이 없어 소장 여부를 조회하지 않았습니다."
                );
            }

            URI uri = UriComponentsBuilder
                    .fromUriString(baseUrl + "/bookExist")
                    .queryParam("authKey", authKey)
                    .queryParam("format", "json")
                    .queryParam("libCode", libCode)
                    .queryParam("isbn13", cleanIsbn)
                    .build()
                    .encode()
                    .toUri();

            log.info("[정보나루 도서 소장 여부 조회 URI] {}", maskAuthKey(uri.toString()));

            String responseBody = data4LibraryClient.get(uri);

            logResponsePreview("정보나루 도서 소장 여부 응답", responseBody);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode responseNode = root.path("response");

            String errCode = text(responseNode, "errCode");
            String error = text(responseNode, "error");

            if (hasText(errCode) || hasText(error)) {
                log.warn("[정보나루 도서 소장 여부 API 에러] errCode={}, error={}", errCode, error);

                return new ExternalBookExistResponse(
                        libCode,
                        cleanIsbn,
                        null,
                        null,
                        false,
                        false,
                        "UNKNOWN",
                        false,
                        "정보나루 도서 소장 여부 조회 중 오류가 발생했습니다: " + error
                );
            }

            JsonNode resultNode = responseNode.path("result");

            String hasBook = text(resultNode, "hasBook");
            String loanAvailable = text(resultNode, "loanAvailable");

            Boolean isOwned = toBooleanYn(hasBook);
            Boolean isLoanAvailable = toBooleanYn(loanAvailable);

            String loanStatus;
            Boolean canApplyHope;

            if (!Boolean.TRUE.equals(isOwned)) {
                loanStatus = "UNKNOWN";
                canApplyHope = true;
            } else if (Boolean.TRUE.equals(isLoanAvailable)) {
                loanStatus = "AVAILABLE";
                canApplyHope = false;
            } else {
                loanStatus = "LOANED";
                canApplyHope = false;
            }

            String message;

            if (Boolean.TRUE.equals(canApplyHope)) {
                message = "해당 도서관에 소장되지 않은 도서입니다. 희망도서 신청이 가능합니다.";
            } else if ("AVAILABLE".equals(loanStatus)) {
                message = "해당 도서관에 소장 중이며 대출 가능합니다.";
            } else {
                message = "해당 도서관에 소장 중이나 현재 대출 가능 상태가 아닙니다.";
            }

            return new ExternalBookExistResponse(
                    libCode,
                    cleanIsbn,
                    hasBook,
                    loanAvailable,
                    isOwned,
                    isLoanAvailable,
                    loanStatus,
                    canApplyHope,
                    message
            );

        } catch (Exception e) {
            log.error("[정보나루 도서 소장 여부 조회 실패]", e);
            throw new RuntimeException("정보나루 도서 소장 여부 조회 API 호출 실패: " + e.getMessage(), e);
        }
    }

    private List<ExternalLibraryResponse> parseLibraries(String responseBody) throws Exception {
        List<ExternalLibraryResponse> result = new ArrayList<>();

        if (responseBody == null || responseBody.isBlank()) {
            return result;
        }

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode responseNode = root.path("response");

        if (responseNode.isMissingNode() || responseNode.isNull()) {
            log.warn("[정보나루 도서관 파싱] response 노드가 없습니다.");
            return result;
        }

        String errCode = text(responseNode, "errCode");
        String error = text(responseNode, "error");

        if (hasText(errCode) || hasText(error)) {
            log.warn("[정보나루 도서관 API 에러] errCode={}, error={}", errCode, error);
            return result;
        }

        JsonNode libsNode = responseNode.path("libs");

        if (libsNode.isMissingNode() || libsNode.isNull()) {
            log.warn("[정보나루 도서관 파싱] libs 노드가 없습니다.");
            return result;
        }

        addLibrariesFromNode(result, libsNode);

        return result;
    }

    private void addBooksFromNode(List<ExternalBookResponse> result, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                addBooksFromNode(result, item);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        JsonNode docNode = node.path("doc");

        if (!docNode.isMissingNode() && !docNode.isNull()) {
            addBooksFromNode(result, docNode);
            return;
        }

        addBookIfValid(result, node);
    }

    private void addLibrariesFromNode(List<ExternalLibraryResponse> result, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                addLibrariesFromNode(result, item);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        JsonNode libNode = node.path("lib");

        if (!libNode.isMissingNode() && !libNode.isNull()) {
            addLibrariesFromNode(result, libNode);
            return;
        }

        addLibraryIfValid(result, node);
    }

    private void addLibraryIfValid(List<ExternalLibraryResponse> result, JsonNode lib) {
        if (lib == null || lib.isMissingNode() || lib.isNull()) {
            return;
        }

        String libCode = text(lib, "libCode");
        String libName = text(lib, "libName");

        if (!hasText(libCode) && !hasText(libName)) {
            return;
        }

        Integer bookCount = integer(lib, "BookCount");

        if (bookCount == null) {
            bookCount = integer(lib, "bookCount");
        }

        result.add(new ExternalLibraryResponse(
                libCode,
                libName,
                text(lib, "address"),
                text(lib, "tel"),
                text(lib, "fax"),
                text(lib, "latitude"),
                text(lib, "longitude"),
                text(lib, "homepage"),
                text(lib, "closed"),
                text(lib, "operatingTime"),
                bookCount
        ));
    }

    private void addBookIfValid(List<ExternalBookResponse> result, JsonNode doc) {
        if (doc == null || doc.isMissingNode() || doc.isNull()) {
            return;
        }

        String isbn13 = text(doc, "isbn13");
        String bookname = text(doc, "bookname");

        if (!hasText(isbn13) && !hasText(bookname)) {
            log.warn("[정보나루 도서 파싱 제외] isbn13/bookname 없음. node={}", doc);
            return;
        }

        ExternalBookResponse book = new ExternalBookResponse(
                bookname,
                text(doc, "authors"),
                text(doc, "publisher"),
                text(doc, "publication_year"),
                isbn13,
                text(doc, "class_no"),
                text(doc, "class_nm"),
                text(doc, "bookImageURL"),
                text(doc, "bookDtlUrl"),
                integer(doc, "loan_count")
        );

        log.info("[정보나루 도서 파싱 성공] bookname={}, isbn13={}",
                book.getBookname(),
                book.getIsbn13()
        );

        result.add(book);
    }

    private List<ExternalBookResponse> removeDuplicateBooks(List<ExternalBookResponse> books) {
        List<ExternalBookResponse> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();

        for (ExternalBookResponse book : books) {
            String key;

            if (hasText(book.getIsbn13())) {
                key = book.getIsbn13();
            } else {
                key = String.valueOf(book.getBookname()) + "_" + String.valueOf(book.getAuthors());
            }

            if (keys.add(key)) {
                result.add(book);
            }
        }

        return result;
    }

    private List<ExternalLibraryResponse> removeDuplicateLibraries(List<ExternalLibraryResponse> libraries) {
        List<ExternalLibraryResponse> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();

        for (ExternalLibraryResponse library : libraries) {
            String libCode = getStringValue(library, "getLibCode", "libCode");
            String libName = getStringValue(library, "getLibName", "libName");
            String address = getStringValue(library, "getAddress", "address");

            String key;

            if (hasText(libCode)) {
                key = libCode;
            } else {
                key = String.valueOf(libName) + "_" + String.valueOf(address);
            }

            if (keys.add(key)) {
                result.add(library);
            }
        }

        return result;
    }

    private String getStringValue(Object target, String getterName, String recordMethodName) {
        if (target == null) {
            return null;
        }

        try {
            Object value = target.getClass().getMethod(getterName).invoke(target);

            if (value == null) {
                return null;
            }

            String text = value.toString();

            return text.isBlank() ? null : text;
        } catch (Exception ignored) {
        }

        try {
            Object value = target.getClass().getMethod(recordMethodName).invoke(target);

            if (value == null) {
                return null;
            }

            String text = value.toString();

            return text.isBlank() ? null : text;
        } catch (Exception ignored) {
        }

        return null;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText();

        if (text == null || text.isBlank()) {
            return null;
        }

        return text;
    }

    private Integer integer(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }

        try {
            return value.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean toBooleanYn(String value) {
        if (value == null) {
            return false;
        }

        return value.equalsIgnoreCase("Y")
                || value.equalsIgnoreCase("YES")
                || value.equals("1")
                || value.equalsIgnoreCase("true");
    }


    private Optional<String> optionalText(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(value.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void logResponsePreview(String title, String responseBody) {
        if (responseBody == null) {
            log.warn("[{}] null", title);
            return;
        }

        int previewLength = Math.min(responseBody.length(), 3000);
        log.info("[{} 앞부분] {}", title, responseBody.substring(0, previewLength));
    }

    private String maskAuthKey(String url) {
        if (url == null || authKey == null || authKey.isBlank()) {
            return url;
        }

        return url.replace(authKey, "****");
    }

    /**
     * ISBN 기준 정보나루 도서 정확 조회.
     */
    public ExternalBookResponse findBookByIsbn(String isbn) {
        String normalizedIsbn = normalizeIsbn(isbn);

        if (!hasText(normalizedIsbn)) {
            throw new IllegalArgumentException(
                    "도서 조회에 필요한 ISBN이 비어 있습니다."
            );
        }

        if (normalizedIsbn.length() != 10
                && normalizedIsbn.length() != 13) {
            throw new IllegalArgumentException(
                    "ISBN은 10자리 또는 13자리여야 합니다."
            );
        }

        List<ExternalBookResponse> books = searchBooks(
                null,
                null,
                null,
                normalizedIsbn,
                null,
                1,
                20
        );

        for (ExternalBookResponse book : books) {
            if (book == null) {
                continue;
            }

            String responseIsbn = normalizeIsbn(
                    book.getIsbn13()
            );

            if (normalizedIsbn.equals(responseIsbn)) {
                return book;
            }
        }

        return null;
    }

    private String normalizeIsbn(String value) {
        if (!hasText(value)) {
            return "";
        }

        return value
                .replaceAll("[^0-9Xx]", "")
                .toUpperCase();
    }

    private boolean isData4LibraryUnavailable(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof Data4LibraryUnavailableException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}