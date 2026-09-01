package com.example.teamproject1.book.controller;

import com.example.teamproject1.book.repository.BookRepository;
import com.example.teamproject1.book.repository.CategoryRepository;
import com.example.teamproject1.book.repository.LibraryRepository;
import com.example.teamproject1.user.entity.User;
import com.example.teamproject1.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JpaTestController {
    private final CategoryRepository categoryRepository;
    private final LibraryRepository libraryRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;

    @GetMapping("/api/jpa-test")
    public Map<String, Object> jpaTest(){
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("categoryCount", categoryRepository.count());
        result.put("libraryCount", libraryRepository.count());
        result.put("userCount", userRepository.count());
        result.put("bookCount", bookRepository.count());

        return result;
    }
}
