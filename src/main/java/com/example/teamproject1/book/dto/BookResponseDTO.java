package com.example.teamproject1.book.dto;

import com.example.teamproject1.book.entity.Book;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;


@Getter
@Builder
public class BookResponseDTO {


    private Long bookId;

    private String title;

    private String author;

    private String publisher;

    private String category;

    private String className;

    private String isbn;

    private String isbn13;

    private String description;

    private String imageUrl;

    private String thumbnailUrl;

    private LocalDate publishedDate;

    private String publicationYear;

    private Integer viewCount;

    private BigDecimal averageRating;

    private Integer totalCount;

    private Integer availableCount;

    private Integer loanCount;


    public static BookResponseDTO fromEntity(Book book) {

        if (book == null) {
            return null;
        }

        String resolvedImageUrl = firstNotBlank(
                book.getImageUrl(),
                book.getThumbnailUrl()
        );

        String resolvedPublicationYear =
                book.getPublishedDate() == null
                        ? null
                        : String.valueOf(
                        book.getPublishedDate().getYear()
                );

        return BookResponseDTO.builder()
                .bookId(book.getBookId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .publisher(book.getPublisher())
                .category(book.getCategoryName())
                .className(book.getCategoryName())
                .isbn(book.getIsbn())
                .isbn13(book.getIsbn())
                .description(book.getDescription())
                .imageUrl(resolvedImageUrl)
                .thumbnailUrl(book.getThumbnailUrl())
                .publishedDate(book.getPublishedDate())
                .publicationYear(resolvedPublicationYear)
                .viewCount(book.getViewCount())
                .averageRating(book.getAverageRating())
                .totalCount(book.getTotalCount())
                .availableCount(book.getAvailableCount())
                .loanCount(book.getLoanCount())
                .build();
    }

    private static String firstNotBlank(
            String first,
            String second
    ) {

        if (first != null && !first.isBlank()) {
            return first;
        }

        return second;
    }
}