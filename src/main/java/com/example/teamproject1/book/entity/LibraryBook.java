package com.example.teamproject1.book.entity;

import com.example.teamproject1.book.entity.enums.LoanStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "library_books",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_library_book",
                        columnNames = {"library_id", "book_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class LibraryBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "library_book_id")
    private Long libraryBookId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "is_owned", nullable = false)
    private Boolean isOwned;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_status", nullable = false)
    private LoanStatus loanStatus;

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "available_count")
    private Integer availableCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}