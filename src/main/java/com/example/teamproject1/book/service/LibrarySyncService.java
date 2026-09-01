package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.ExternalLibraryResponse;
import com.example.teamproject1.book.dto.LibrarySyncResponse;
import com.example.teamproject1.book.entity.Library;
import com.example.teamproject1.book.repository.LibraryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LibrarySyncService {

    private final Data4LibraryService data4LibraryService;
    private final LibraryRepository libraryRepository;

    @Transactional
    public LibrarySyncResponse syncLibraries(
            Integer pageNo,
            Integer pageSize,
            String region,
            String dtlRegion,
            String libCode
    ) {
        List<ExternalLibraryResponse> externalLibraries =
                data4LibraryService.searchLibraries(
                        pageNo,
                        pageSize,
                        region,
                        dtlRegion,
                        libCode
                );

        int insertedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (ExternalLibraryResponse external : externalLibraries) {

            if (external.getLibCode() == null || external.getLibCode().isBlank()) {
                skippedCount++;
                continue;
            }

            Library library = libraryRepository.findByLibCode(external.getLibCode())
                    .orElse(null);

            if (library == null) {
                library = new Library();
                library.setLibCode(external.getLibCode());
                library.setCreatedAt(LocalDateTime.now());
                insertedCount++;
            } else {
                updatedCount++;
            }

            library.setLibraryName(external.getLibName());
            library.setAddress(external.getAddress());
            library.setPhone(external.getTel());

            libraryRepository.save(library);
        }

        return new LibrarySyncResponse(
                externalLibraries.size(),
                insertedCount,
                updatedCount,
                skippedCount,
                "정보나루 도서관 정보 동기화가 완료되었습니다."
        );
    }
}