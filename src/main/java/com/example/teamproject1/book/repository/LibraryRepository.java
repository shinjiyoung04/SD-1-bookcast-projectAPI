package com.example.teamproject1.book.repository;

import com.example.teamproject1.book.entity.Library;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LibraryRepository extends JpaRepository<Library, Long> {

    Optional<Library> findByLibCode(String libCode);
}
