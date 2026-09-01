package com.example.teamproject1.book.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "books")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long bookId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 100)
    private String author;

    @Column(length = 100)
    private String publisher;


    @Column(name = "category", length = 100)
    private String categoryName;

    @Column(length = 30, unique = true)
    private String isbn;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "published_date")
    private LocalDate publishedDate;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "available_count")
    private Integer availableCount;

    @Column(name = "loan_count")
    private Integer loanCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.totalCount == null) {
            this.totalCount = 1;
        }

        if (this.availableCount == null) {
            this.availableCount = this.totalCount;
        }

        if (this.loanCount == null) {
            this.loanCount = 0;
        }

        if (this.viewCount == null) {
            this.viewCount = 0;
        }

        if (this.averageRating == null) {
            this.averageRating = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}