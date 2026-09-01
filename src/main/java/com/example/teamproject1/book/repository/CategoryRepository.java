package com.example.teamproject1.book.repository;

import com.example.teamproject1.book.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
