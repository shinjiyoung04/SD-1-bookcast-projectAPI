package com.example.teamproject1.book.controller;

import com.example.teamproject1.book.dto.UserApplicationListResponse;
import com.example.teamproject1.book.service.UserApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserApplicationController {
    private  final UserApplicationService userApplicationService;

    @GetMapping("/api/users/{userId}/applications")
    public List<UserApplicationListResponse> getMyApplications
            (
                    @PathVariable Long userId
            ){
                return userApplicationService.getMyApplications(userId);
    }
}
