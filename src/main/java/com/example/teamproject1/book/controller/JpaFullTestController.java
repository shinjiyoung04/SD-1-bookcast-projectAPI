package com.example.teamproject1.book.controller;

import com.example.teamproject1.book.repository.*;
import com.example.teamproject1.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JpaFullTestController {

    private final CategoryRepository categoryRepository;
    private final LibraryRepository libraryRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final LibraryBookRepository libraryBookRepository;
    private final HopeApplicationRepository hopeApplicationRepository;
    private final HopeVoteRepository hopeVoteRepository;
    private final AiPredictionRepository aiPredictionRepository;

    @GetMapping("/api/jpa-full-test")
    public Map<String, Object> jpaFullTest() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("categoryCount", categoryRepository.count());
        result.put("libraryCount", libraryRepository.count());
        result.put("userCount", userRepository.count());
        result.put("bookCount", bookRepository.count());
        result.put("libraryBookCount", libraryBookRepository.count());
        result.put("hopeApplicationCount", hopeApplicationRepository.count());
        result.put("hopeVoteCount", hopeVoteRepository.count());
        result.put("aiPredictionCount", aiPredictionRepository.count());

        return result;
    }
}