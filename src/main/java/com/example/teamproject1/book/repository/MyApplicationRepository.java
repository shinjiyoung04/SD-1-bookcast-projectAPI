package com.example.teamproject1.book.repository;

import com.example.teamproject1.book.entity.HopeApplication;
import com.example.teamproject1.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.List;


public interface MyApplicationRepository
        extends Repository<HopeApplication, Long> {

    @EntityGraph(attributePaths = {"book", "library"})
    List<HopeApplication> findAllByUserOrderByCreatedAtDesc(User user);
}