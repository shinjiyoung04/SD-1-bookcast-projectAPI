package com.example.teamproject1.libraryinfo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class Data4LibraryManagedLibraryClient {

    @Value("${data4library.base-url:http://data4library.kr/api}")
    private String baseUrl;

    @Value("${data4library.auth-key:}")
    private String authKey;

    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.create();

    public ExternalLibraryInfo fetchLibraryInfo(String libraryCode) {
        if (!StringUtils.hasText(libraryCode)) {
            return ExternalLibraryInfo.unavailable(
                    "정보나루 도서관 코드가 없습니다."
            );
        }

        if (!StringUtils.hasText(authKey)) {
            return ExternalLibraryInfo.unavailable(
                    "정보나루 인증키가 설정되지 않았습니다."
            );
        }

        URI uri = UriComponentsBuilder
                .fromUriString(normalizeBaseUrl() + "/libSrch")
                .queryParam("authKey", authKey)
                .queryParam("format", "json")
                .queryParam("libCode", libraryCode)
                .queryParam("pageNo", 1)
                .queryParam("pageSize", 10)
                .build()
                .encode()
                .toUri();

        try {
            log.info(
                    "[Data4LibraryManagedLibraryClient] 담당 도서관 조회: {}",
                    maskAuthKey(uri.toString())
            );

            String responseBody = restClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) {
                return ExternalLibraryInfo.unavailable(
                        "정보나루 응답이 비어 있습니다."
                );
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode response = root.has("response")
                    ? root.path("response")
                    : root;

            String errorMessage = firstText(
                    response,
                    "errorMessage",
                    "errMsg",
                    "error"
            );

            if (StringUtils.hasText(errorMessage)) {
                return ExternalLibraryInfo.unavailable(
                        "정보나루 응답 오류: " + errorMessage
                );
            }

            List<JsonNode> libraries = extractLibraryNodes(
                    response.path("libs")
            );

            JsonNode matched = libraries
                    .stream()
                    .filter(library -> libraryCode.equals(
                            firstText(
                                    library,
                                    "libCode",
                                    "lib_code"
                            )
                    ))
                    .findFirst()
                    .orElse(
                            libraries.isEmpty()
                                    ? null
                                    : libraries.get(0)
                    );

            if (matched == null) {
                return ExternalLibraryInfo.unavailable(
                        "해당 코드의 참여 도서관 정보를 찾지 못했습니다."
                );
            }

            return new ExternalLibraryInfo(
                    firstText(matched, "libCode", "lib_code"),
                    firstText(matched, "libName", "libraryName", "name"),
                    firstText(matched, "address", "addr"),
                    firstText(matched, "tel", "phone", "telephone"),
                    firstText(matched, "fax"),
                    firstText(matched, "homepage", "homepageUrl"),
                    firstText(matched, "closed", "closedDay", "closedDays"),
                    firstText(matched, "operatingTime", "operating_time"),
                    firstLong(matched, "BookCount", "bookCount", "book_count"),
                    firstText(matched, "latitude", "lat"),
                    firstText(matched, "longitude", "lon", "lng"),
                    true,
                    null
            );
        } catch (Exception exception) {
            log.warn(
                    "[Data4LibraryManagedLibraryClient] 담당 도서관 정보 조회 실패. libCode={}",
                    libraryCode,
                    exception
            );

            return ExternalLibraryInfo.unavailable(
                    "정보나루 연결에 실패했습니다: " + safeMessage(exception)
            );
        }
    }

    private List<JsonNode> extractLibraryNodes(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        collectLibraryNodes(node, result);
        return result;
    }

    private void collectLibraryNodes(
            JsonNode node,
            List<JsonNode> result
    ) {
        if (
                node == null
                        || node.isMissingNode()
                        || node.isNull()
        ) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                collectLibraryNodes(item, result);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        JsonNode wrappedLibrary = node.path("lib");

        if (
                !wrappedLibrary.isMissingNode()
                        && !wrappedLibrary.isNull()
        ) {
            collectLibraryNodes(wrappedLibrary, result);
            return;
        }

        if (
                StringUtils.hasText(
                        firstText(node, "libCode", "lib_code")
                )
                        || StringUtils.hasText(
                        firstText(node, "libName", "libraryName")
                )
        ) {
            result.add(node);
        }
    }

    private String firstText(
            JsonNode node,
            String... fields
    ) {
        for (String field : fields) {
            JsonNode value = node.path(field);

            if (
                    !value.isMissingNode()
                            && !value.isNull()
            ) {
                String text = value.asText();

                if (StringUtils.hasText(text)) {
                    return text.trim();
                }
            }
        }

        return null;
    }

    private long firstLong(
            JsonNode node,
            String... fields
    ) {
        for (String field : fields) {
            JsonNode value = node.path(field);

            if (
                    value.isMissingNode()
                            || value.isNull()
            ) {
                continue;
            }

            if (value.isNumber()) {
                return value.asLong();
            }

            String text = value.asText();

            if (!StringUtils.hasText(text)) {
                continue;
            }

            try {
                return Long.parseLong(
                        text.replaceAll("[^0-9-]", "")
                );
            } catch (NumberFormatException ignored) {
                // 다음 필드 확인
            }
        }

        return 0L;
    }

    private String normalizeBaseUrl() {
        return baseUrl == null
                ? "http://data4library.kr/api"
                : baseUrl.replaceAll("/+$", "");
    }

    private String maskAuthKey(String url) {
        if (!StringUtils.hasText(authKey)) {
            return url;
        }

        return url.replace(authKey, "****");
    }

    private String safeMessage(Exception exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    public record ExternalLibraryInfo(
            String libraryCode,
            String libraryName,
            String address,
            String tel,
            String fax,
            String homepage,
            String closed,
            String operatingTime,
            long bookCount,
            String latitude,
            String longitude,
            boolean available,
            String message
    ) {
        public static ExternalLibraryInfo unavailable(String message) {
            return new ExternalLibraryInfo(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0L,
                    null,
                    null,
                    false,
                    message
            );
        }
    }
}
