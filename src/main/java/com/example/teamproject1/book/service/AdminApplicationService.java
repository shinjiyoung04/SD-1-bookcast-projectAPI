package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.AdminApplicationListResponse;
import com.example.teamproject1.book.dto.AdminApplicationProcessResponse;
import com.example.teamproject1.book.dto.ApproveApplicationRequest;
import com.example.teamproject1.book.dto.RejectApplicationRequest;
import com.example.teamproject1.book.dto.RejectApplicationResponse;
import com.example.teamproject1.book.entity.*;
import com.example.teamproject1.book.entity.enums.ApplicationStatus;
import com.example.teamproject1.book.entity.enums.LoanStatus;
import com.example.teamproject1.user.entity.UserRole;
import com.example.teamproject1.book.repository.HopeApplicationRepository;
import com.example.teamproject1.book.repository.LibraryBookRepository;
import com.example.teamproject1.user.repository.UserRepository;
import com.example.teamproject1.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminApplicationService {

    private final HopeApplicationRepository hopeApplicationRepository;
    private final UserRepository userRepository;
    private final LibraryBookRepository libraryBookRepository;

    public List<AdminApplicationListResponse> getApplicationList() {
        return hopeApplicationRepository.findAdminApplicationList();
    }

    @Transactional
    public AdminApplicationProcessResponse approveApplication(
            Long applicationId,
            ApproveApplicationRequest request
    ) {
        HopeApplication application = hopeApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 희망도서 신청입니다."
                ));

        com.example.teamproject1.user.entity.User admin = userRepository.findById(request.getAdminId())
                .orElseThrow();

        if (admin.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ADMIN 권한 사용자만 승인 처리할 수 있습니다."
            );
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PENDING 상태의 신청만 승인할 수 있습니다."
            );
        }

        Book book = application.getBook();
        Library library = application.getLibrary();

        if (book == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "신청에 연결된 도서 정보가 없습니다."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        application.setStatus(ApplicationStatus.APPROVED);
        application.setAdmin(admin);
        application.setAdminComment(
                request.getAdminComment() == null || request.getAdminComment().isBlank()
                        ? "승인 처리 완료"
                        : request.getAdminComment()
        );
        application.setProcessedAt(now);

        LibraryBook libraryBook = libraryBookRepository
                .findByLibrary_LibraryIdAndBook_BookId(
                        library.getLibraryId(),
                        book.getBookId()
                )
                .orElseGet(() -> {
                    LibraryBook newLibraryBook = new LibraryBook();
                    newLibraryBook.setLibrary(library);
                    newLibraryBook.setBook(book);
                    newLibraryBook.setCreatedAt(now);
                    return newLibraryBook;
                });

        int currentTotalCount = libraryBook.getTotalCount() == null
                ? 0
                : libraryBook.getTotalCount();

        int currentAvailableCount = libraryBook.getAvailableCount() == null
                ? 0
                : libraryBook.getAvailableCount();

        libraryBook.setIsOwned(true);
        libraryBook.setLoanStatus(LoanStatus.AVAILABLE);
        libraryBook.setTotalCount(Math.max(currentTotalCount, 1));
        libraryBook.setAvailableCount(Math.max(currentAvailableCount, 1));
        libraryBook.setUpdatedAt(now);

        LibraryBook savedLibraryBook = libraryBookRepository.save(libraryBook);

        return new AdminApplicationProcessResponse(
                application.getApplicationId(),
                application.getTitle(),
                application.getStatus(),
                admin.getUserId(),
                application.getAdminComment(),
                application.getProcessedAt(),
                savedLibraryBook.getIsOwned(),
                savedLibraryBook.getLoanStatus(),
                savedLibraryBook.getTotalCount(),
                savedLibraryBook.getAvailableCount(),
                "희망도서 신청이 승인되었습니다."
        );
    }

    @Transactional
    public RejectApplicationResponse rejectApplication(
            Long applicationId,
            RejectApplicationRequest request
    ) {
        HopeApplication application = hopeApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 희망도서 신청입니다."
                ));

        User admin = userRepository.findById(request.getAdminId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 관리자입니다."
                ));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ADMIN 권한 사용자만 반려 처리할 수 있습니다."
            );
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PENDING 상태의 신청만 반려할 수 있습니다."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        application.setStatus(ApplicationStatus.REJECTED);
        application.setAdmin(admin);
        application.setAdminComment(request.getAdminComment());
        application.setProcessedAt(now);

        return new RejectApplicationResponse(
                application.getApplicationId(),
                application.getTitle(),
                application.getStatus(),
                admin.getUserId(),
                application.getAdminComment(),
                application.getProcessedAt(),
                "희망도서 신청이 반려되었습니다."
        );
    }
}