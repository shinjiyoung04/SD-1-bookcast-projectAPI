package com.example.teamproject1.book.repository;

import com.example.teamproject1.book.entity.LibraryBook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LibraryBookRepository extends JpaRepository<LibraryBook, Long> {

    Optional<LibraryBook> findByLibrary_LibraryIdAndBook_BookId(Long libraryId, Long bookId);
}
