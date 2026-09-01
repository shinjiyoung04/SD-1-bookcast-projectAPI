package com.example.teamproject1.memberaccount.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProfileVerificationService {

    private static final int EXPIRES_MINUTES = 10;

    private final Map<String, VerificationEntry>
            verificationEntries =
            new ConcurrentHashMap<>();

    public TokenIssue issue(Long userId) {
        invalidateUser(userId);
        removeExpiredEntries();

        String token = UUID.randomUUID().toString();

        LocalDateTime expiresAt =
                LocalDateTime.now()
                        .plusMinutes(EXPIRES_MINUTES);

        verificationEntries.put(
                token,
                new VerificationEntry(
                        userId,
                        expiresAt
                )
        );

        return new TokenIssue(token, expiresAt);
    }

    public void validate(
            Long userId,
            String token
    ) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "비밀번호 확인이 필요합니다."
            );
        }

        VerificationEntry entry =
                verificationEntries.get(token);

        if (entry == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "비밀번호 확인 정보가 없거나 만료되었습니다."
            );
        }

        if (!entry.userId().equals(userId)) {
            verificationEntries.remove(token);

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "다른 사용자의 인증 정보는 사용할 수 없습니다."
            );
        }

        if (entry.expiresAt().isBefore(
                LocalDateTime.now()
        )) {
            verificationEntries.remove(token);

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "비밀번호 확인 시간이 만료되었습니다. 다시 확인해주세요."
            );
        }
    }

    public void invalidate(String token) {
        if (token != null) {
            verificationEntries.remove(token);
        }
    }

    public void invalidateUser(Long userId) {
        verificationEntries.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        .userId()
                                        .equals(userId)
                );
    }

    private void removeExpiredEntries() {
        LocalDateTime now =
                LocalDateTime.now();

        verificationEntries.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue()
                                        .expiresAt()
                                        .isBefore(now)
                );
    }

    private record VerificationEntry(
            Long userId,
            LocalDateTime expiresAt
    ) {
    }

    public record TokenIssue(
            String token,
            LocalDateTime expiresAt
    ) {
    }
}
