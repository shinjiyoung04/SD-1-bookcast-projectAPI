package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.CreateApplicationRequest;
import com.example.teamproject1.book.dto.CreateApplicationResponse;
import com.example.teamproject1.book.dto.ExternalBookExistResponse;
import com.example.teamproject1.book.dto.MyApplicationResponse;
import com.example.teamproject1.book.entity.AiPrediction;
import com.example.teamproject1.book.entity.Book;
import com.example.teamproject1.book.entity.Category;
import com.example.teamproject1.book.entity.HopeApplication;
import com.example.teamproject1.book.entity.Library;
import com.example.teamproject1.book.entity.enums.ApplicationStatus;
import com.example.teamproject1.book.repository.AiPredictionRepository;
import com.example.teamproject1.book.repository.BookRepository;
import com.example.teamproject1.book.repository.CategoryRepository;
import com.example.teamproject1.book.repository.HopeApplicationRepository;
import com.example.teamproject1.book.repository.LibraryRepository;
import com.example.teamproject1.book.repository.MyApplicationRepository;
import com.example.teamproject1.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final com.example.teamproject1.user.repository.UserRepository userRepository;

    private final LibraryRepository libraryRepository;
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final HopeApplicationRepository hopeApplicationRepository;
    private final AiPredictionRepository aiPredictionRepository;

    // 마이페이지 사용자별 신청 목록 조회 전용 Repository
    private final MyApplicationRepository myApplicationRepository;

    private final Data4LibraryService data4LibraryService;

    /**
     * 희망도서 신청
     */
    @Transactional
    public CreateApplicationResponse createApplication(
            CreateApplicationRequest request
    ) {

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 사용자입니다."
                ));

        Library library = resolveLibrary(request);

        Category category = resolveCategory(request);

        if (library.getLibCode() == null ||
                library.getLibCode().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "해당 도서관은 정보나루 libCode가 등록되어 있지 않습니다."
            );
        }

        ExternalBookExistResponse existResponse =
                data4LibraryService.checkBookExist(
                        library.getLibCode(),
                        request.getIsbn()
                );

        if (!Boolean.TRUE.equals(existResponse.getCanApplyHope())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미 해당 도서관에 소장 중인 도서이므로 희망도서 신청이 불가능합니다."
            );
        }

        /*
         * orElseGet() 람다 내부에서 사용하기 위해
         * category를 effectively final 상태로 만든다.
         */
        final Category finalCategory = category;

        Book book = bookRepository.findByIsbn(request.getIsbn())
                .orElseGet(() ->
                        createBook(request, finalCategory)
                );

        /*
         * 요청에서 categoryId가 전달되지 않은 경우,
         * 기존 도서에 연결된 카테고리를 사용한다.
         */
        if (category == null) {
            category = book.getCategory();
        }

        HopeApplication application = new HopeApplication();

        application.setUser(user);
        application.setBook(book);
        application.setIsbn(book.getIsbn());
        application.setTitle(book.getTitle());
        application.setAuthor(book.getAuthor());
        application.setCategory(category);
        application.setLibrary(library);
        application.setReason(request.getReason());
        application.setStatus(ApplicationStatus.PENDING);
        application.setAdmin(null);
        application.setAdminComment(null);
        application.setCreatedAt(LocalDateTime.now());
        application.setProcessedAt(null);

        HopeApplication savedApplication =
                hopeApplicationRepository.save(application);

        /*
         * 현재는 테스트용 고정 AI 예측 값이다.
         * 추후 실제 AI 서버 응답으로 교체할 수 있다.
         */
        BigDecimal approvalProbability =
                new BigDecimal("55.00");

        BigDecimal popularityScore =
                new BigDecimal("60.00");

        BigDecimal voteAdjustment =
                new BigDecimal("0.00");

        BigDecimal finalScore =
                new BigDecimal("57.50");

        AiPrediction prediction = new AiPrediction();

        prediction.setApplication(savedApplication);
        prediction.setBook(book);
        prediction.setApprovalProbability(approvalProbability);
        prediction.setPopularityScore(popularityScore);
        prediction.setVoteAdjustment(voteAdjustment);
        prediction.setFinalScore(finalScore);
        prediction.setModelVersion("test-v1");
        prediction.setCreatedAt(LocalDateTime.now());

        aiPredictionRepository.save(prediction);

        return new CreateApplicationResponse(
                savedApplication.getApplicationId(),
                savedApplication.getTitle(),
                savedApplication.getAuthor(),
                savedApplication.getStatus(),
                approvalProbability,
                popularityScore,
                finalScore,
                "희망도서 신청이 완료되었습니다."
        );
    }

    /**
     * 마이페이지 사용자별 희망도서 신청 목록 조회
     */
    @Transactional(readOnly = true)
    public List<MyApplicationResponse> getMyApplications(
            Long userId
    ) {
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "사용자 ID가 필요합니다."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 사용자입니다."
                ));

        return myApplicationRepository
                .findAllByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toMyApplicationResponse)
                .toList();
    }

    /**
     * HopeApplication 엔티티를
     * 마이페이지 응답 DTO로 변환한다.
     */
    private MyApplicationResponse toMyApplicationResponse(
            HopeApplication application
    ) {
        Book book = application.getBook();
        Library library = application.getLibrary();

        /*
         * 관리자 처리 의견이 존재하면 우선 표시하고,
         * 아직 처리되지 않았다면 사용자의 신청 사유를 표시한다.
         */
        String displayReason = application.getAdminComment();

        if (displayReason == null || displayReason.isBlank()) {
            displayReason = application.getReason();
        }

        return MyApplicationResponse.builder()
                .applicationId(application.getApplicationId())
                .bookTitle(application.getTitle())
                .author(application.getAuthor())
                .publisher(
                        book != null
                                ? book.getPublisher()
                                : null
                )
                .isbn13(application.getIsbn())
                .libraryName(
                        library != null
                                ? library.getLibraryName()
                                : null
                )
                .status(
                        application.getStatus() != null
                                ? application.getStatus().name()
                                : null
                )
                .reason(displayReason)
                .createdAt(application.getCreatedAt())
                .processedAt(application.getProcessedAt())
                .build();
    }

    /**
     * 희망도서 신청 대상 도서관 조회 또는 생성
     */
    private Library resolveLibrary(
            CreateApplicationRequest request
    ) {
        if (request.getLibraryId() != null) {
            return libraryRepository
                    .findById(request.getLibraryId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "존재하지 않는 도서관입니다."
                            )
                    );
        }

        if (request.getLibCode() == null ||
                request.getLibCode().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "신청 도서관 libCode가 필요합니다."
            );
        }

        return libraryRepository
                .findByLibCode(request.getLibCode())
                .orElseGet(() -> {
                    Library library = new Library();

                    library.setLibCode(request.getLibCode());

                    library.setLibraryName(
                            request.getLibraryName() == null ||
                                    request.getLibraryName().isBlank()
                                    ? "도서관명 없음"
                                    : request.getLibraryName()
                    );

                    library.setAddress(
                            request.getLibraryAddress()
                    );

                    library.setPhone(
                            request.getLibraryPhone()
                    );

                    library.setCreatedAt(
                            LocalDateTime.now()
                    );

                    return libraryRepository.save(library);
                });
    }

    /**
     * 요청에 카테고리가 포함된 경우 카테고리 조회
     */
    private Category resolveCategory(
            CreateApplicationRequest request
    ) {
        if (request.getCategoryId() == null) {
            return null;
        }

        return categoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "존재하지 않는 카테고리입니다."
                        )
                );
    }

    /**
     * ISBN에 해당하는 도서가 DB에 없을 경우 새로 등록
     */
    private Book createBook(
            CreateApplicationRequest request,
            Category category
    ) {
        Book newBook = new Book();

        newBook.setIsbn(request.getIsbn());
        newBook.setTitle(request.getTitle());
        newBook.setAuthor(request.getAuthor());
        newBook.setPublisher(request.getPublisher());
        newBook.setPublishedDate(request.getPublishedDate());
        newBook.setCategory(category);

        newBook.setDescription(
                "희망도서 신청 과정에서 등록된 테스트 도서입니다."
        );

        newBook.setThumbnailUrl(null);
        newBook.setViewCount(0);
        newBook.setAverageRating(BigDecimal.ZERO);
        newBook.setCreatedAt(LocalDateTime.now());
        newBook.setUpdatedAt(LocalDateTime.now());

        return bookRepository.save(newBook);
    }
}