package com.example.teamproject1.data4library;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class Data4LibraryCachedClient {

    private final RestClient restClient;
    private final Duration freshDuration;
    private final Duration staleDuration;

    private final Map<String, CacheEntry> cache =
            new ConcurrentHashMap<>();

    private final Map<String, CompletableFuture<String>> inFlight =
            new ConcurrentHashMap<>();

    public Data4LibraryCachedClient(
            @Qualifier("data4LibraryRestClient")
            RestClient restClient,

            @Value("${data4library.cache-fresh-minutes:10}")
            long freshMinutes,

            @Value("${data4library.cache-stale-hours:24}")
            long staleHours
    ) {
        this.restClient = restClient;
        this.freshDuration = Duration.ofMinutes(
                Math.max(1, freshMinutes)
        );
        this.staleDuration = Duration.ofHours(
                Math.max(1, staleHours)
        );
    }

    // 동일 URI 요청은 하나의 HTTP 요청으로 합치기
    public String get(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("정보나루 요청 URI가 없습니다.");
        }

        String key = uri.toString();
        Instant now = Instant.now();

        CacheEntry cached = cache.get(key);

        if (isFresh(cached, now)) {
            return cached.body();
        }

        CompletableFuture<String> newFuture =
                new CompletableFuture<>();

        CompletableFuture<String> existingFuture =
                inFlight.putIfAbsent(key, newFuture);

        if (existingFuture != null) {
            return join(existingFuture);
        }

        try {
            String responseBody = restClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            validateBody(responseBody);

            cache.put(
                    key,
                    new CacheEntry(responseBody, Instant.now())
            );

            newFuture.complete(responseBody);
            return responseBody;

        } catch (Exception exception) {
            CacheEntry stale = cache.get(key);

            if (isUsableStale(stale, Instant.now())) {
                log.warn(
                        "[정보나루 캐시 대체] 외부 API 호출 실패로 이전 정상 응답을 사용합니다. uri={}, ageSeconds={}, message={}",
                        maskAuthKey(key),
                        Duration.between(
                                stale.savedAt(),
                                Instant.now()
                        ).toSeconds(),
                        exception.getMessage()
                );

                newFuture.complete(stale.body());
                return stale.body();
            }

            Data4LibraryUnavailableException unavailableException =
                    new Data4LibraryUnavailableException(
                            "정보나루 API가 제한 시간 안에 응답하지 않았습니다.",
                            exception
                    );

            newFuture.completeExceptionally(unavailableException);
            throw unavailableException;

        } finally {
            inFlight.remove(key, newFuture);
        }
    }

    private String join(CompletableFuture<String> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new Data4LibraryUnavailableException(
                    "정보나루 API 공동 요청 처리에 실패했습니다.",
                    cause == null ? exception : cause
            );
        }
    }

    private void validateBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new Data4LibraryUnavailableException(
                    "정보나루 응답 본문이 비어 있습니다.",
                    null
            );
        }

        String trimmed = responseBody.stripLeading().toLowerCase();

        if (
                trimmed.startsWith("<!doctype html")
                        || trimmed.startsWith("<html")
        ) {
            throw new Data4LibraryUnavailableException(
                    "정보나루가 JSON 대신 오류 HTML을 반환했습니다.",
                    null
            );
        }
    }

    private boolean isFresh(CacheEntry entry, Instant now) {
        return entry != null
                && Duration.between(entry.savedAt(), now)
                .compareTo(freshDuration) <= 0;
    }

    private boolean isUsableStale(CacheEntry entry, Instant now) {
        return entry != null
                && Duration.between(entry.savedAt(), now)
                .compareTo(staleDuration) <= 0;
    }

    private String maskAuthKey(String url) {
        if (url == null) {
            return null;
        }

        return url.replaceAll(
                "([?&]authKey=)[^&]+",
                "$1****"
        );
    }

    private record CacheEntry(
            String body,
            Instant savedAt
    ) {
    }
}
