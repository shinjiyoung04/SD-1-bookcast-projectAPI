package com.example.teamproject1.book.service;

import com.example.teamproject1.user.entity.User;

import com.example.teamproject1.book.dto.VoteApplicationResponse;
import com.example.teamproject1.book.dto.VoteCreateResponse;
import com.example.teamproject1.book.entity.*;
import com.example.teamproject1.book.entity.enums.ApplicationStatus;
import com.example.teamproject1.book.repository.HopeApplicationRepository;
import com.example.teamproject1.book.repository.HopeVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VoteService {

    private final HopeApplicationRepository hopeApplicationRepository;
    private final HopeVoteRepository hopeVoteRepository;
    private final com.example.teamproject1.user.repository.UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<VoteApplicationResponse> getVoteApplications(Long userId) {
        List<HopeApplication> applications =
                hopeApplicationRepository.findVoteApplicationsByStatus(ApplicationStatus.PENDING);

        return applications.stream()
                .map(application -> toVoteApplicationResponse(application, userId))
                .toList();
    }

    @Transactional
    public VoteCreateResponse createVote(Long applicationId, Long userId) {
        HopeApplication application = hopeApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 희망도서 신청입니다."
                ));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "심사 중인 희망도서만 함께 신청할 수 있습니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 사용자입니다."
                ));

        boolean alreadyVoted = hopeVoteRepository.existsByApplicationApplicationIdAndUserUserId(
                applicationId,
                userId
        );

        if (alreadyVoted) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미 함께 신청한 도서입니다."
            );
        }

        HopeVote vote = new HopeVote();
        vote.setApplication(application);
        vote.setUser(user);
        vote.setCreatedAt(LocalDateTime.now());

        hopeVoteRepository.save(vote);

        long voteCount = hopeVoteRepository.countByApplicationApplicationId(applicationId);

        return new VoteCreateResponse(
                applicationId,
                voteCount,
                "함께 신청이 완료되었습니다."
        );
    }

    private VoteApplicationResponse toVoteApplicationResponse(HopeApplication application, Long userId) {
        Book book = application.getBook();
        Library library = application.getLibrary();

        Category category = null;

        if (book != null) {
            category = book.getCategory();
        }

        long voteCount = hopeVoteRepository.countByApplicationApplicationId(
                application.getApplicationId()
        );

        boolean alreadyVoted = false;

        if (userId != null) {
            alreadyVoted = hopeVoteRepository.existsByApplicationApplicationIdAndUserUserId(
                    application.getApplicationId(),
                    userId
            );
        }

        return new VoteApplicationResponse(
                application.getApplicationId(),
                book != null ? book.getBookId() : null,
                application.getIsbn(),
                application.getTitle(),
                application.getAuthor(),
                book != null ? book.getPublisher() : null,
                book != null ? book.getPublishedDate() : null,
                category != null ? category.getCategoryName() : null,
                book != null ? book.getThumbnailUrl() : null,
                library != null ? library.getLibraryName() : null,
                application.getReason(),
                application.getStatus(),
                voteCount,
                application.getCreatedAt(),
                alreadyVoted
        );
    }
}