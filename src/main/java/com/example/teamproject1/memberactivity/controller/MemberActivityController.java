package com.example.teamproject1.memberactivity.controller;

import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.BookInteractionRequest;
import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.BookUserStateResponse;
import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.LikedBookResponse;
import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.ReviewCreateRequest;
import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.ReviewResponse;
import com.example.teamproject1.memberactivity.dto.MemberActivityDtos.VotedApplicationResponse;
import com.example.teamproject1.memberactivity.service.MemberActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MemberActivityController {

    private final MemberActivityService memberActivityService;

    @GetMapping("/books/{isbn}/user-state")
    public ResponseEntity<BookUserStateResponse> getBookUserState(
            @PathVariable String isbn,
            @RequestParam(required = false) Long userId
    ) {
        return ResponseEntity.ok(
                memberActivityService.getBookUserState(userId, isbn)
        );
    }

    @PostMapping("/books/{isbn}/like")
    public ResponseEntity<BookUserStateResponse> likeBook(
            @PathVariable String isbn,
            @RequestParam Long userId,
            @Valid @RequestBody BookInteractionRequest request
    ) {
        return ResponseEntity.ok(
                memberActivityService.likeBook(userId, isbn, request)
        );
    }

    @DeleteMapping("/books/{isbn}/like")
    public ResponseEntity<BookUserStateResponse> unlikeBook(
            @PathVariable String isbn,
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(
                memberActivityService.unlikeBook(userId, isbn)
        );
    }

    @PostMapping("/books/{isbn}/wishlist")
    public ResponseEntity<BookUserStateResponse> addWishlist(
            @PathVariable String isbn,
            @RequestParam Long userId,
            @Valid @RequestBody BookInteractionRequest request
    ) {
        return ResponseEntity.ok(
                memberActivityService.addWishlist(userId, isbn, request)
        );
    }

    @DeleteMapping("/books/{isbn}/wishlist")
    public ResponseEntity<BookUserStateResponse> removeWishlist(
            @PathVariable String isbn,
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(
                memberActivityService.removeWishlist(userId, isbn)
        );
    }

    @GetMapping("/books/{isbn}/reviews")
    public ResponseEntity<List<ReviewResponse>> getReviews(
            @PathVariable String isbn
    ) {
        return ResponseEntity.ok(
                memberActivityService.getReviews(isbn)
        );
    }

    @PostMapping("/books/{isbn}/reviews")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable String isbn,
            @RequestParam Long userId,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        return ResponseEntity.ok(
                memberActivityService.createReview(userId, isbn, request)
        );
    }

    @GetMapping("/member-activity/{userId}/liked-books")
    public ResponseEntity<List<LikedBookResponse>> getLikedBooks(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                memberActivityService.getLikedBooks(userId)
        );
    }

    @GetMapping("/member-activity/{userId}/voted-applications")
    public ResponseEntity<List<VotedApplicationResponse>> getVotedApplications(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                memberActivityService.getVotedApplications(userId)
        );
    }
}
