package com.example.teamproject1.admin.controller;

import com.example.teamproject1.admin.dto.*;
import com.example.teamproject1.admin.service.AdminManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminManagementController {

    private final AdminManagementService adminManagementService;

    @GetMapping("/me")
    public ResponseEntity<AdminSelfResponse> getMe(
            @RequestParam Long requesterUserId
    ) {
        return ResponseEntity.ok(
                adminManagementService.getAdminSelf(requesterUserId)
        );
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard(
            @RequestParam Long requesterUserId
    ) {
        return ResponseEntity.ok(
                adminManagementService.getDashboard(requesterUserId)
        );
    }

    @GetMapping("/applications")
    public ResponseEntity<AdminApplicationPageResponse> getApplications(
            @RequestParam Long requesterUserId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResponseEntity.ok(
                adminManagementService.getApplications(
                        requesterUserId,
                        keyword,
                        status,
                        sort,
                        page,
                        pageSize
                )
        );
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<AdminApplicationItemResponse> getApplication(
            @PathVariable Long applicationId,
            @RequestParam Long requesterUserId
    ) {
        return ResponseEntity.ok(
                adminManagementService.getApplication(
                        requesterUserId,
                        applicationId
                )
        );
    }

    @PatchMapping("/applications/{applicationId}/decision")
    public ResponseEntity<AdminApplicationItemResponse> decideApplication(
            @PathVariable Long applicationId,
            @Valid @RequestBody AdminApplicationDecisionRequest request
    ) {
        return ResponseEntity.ok(
                adminManagementService.decideApplication(
                        applicationId,
                        request
                )
        );
    }

    @GetMapping("/members")
    public ResponseEntity<AdminMemberPageResponse> getMembers(
            @RequestParam Long requesterUserId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String role,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResponseEntity.ok(
                adminManagementService.getMembers(
                        requesterUserId,
                        keyword,
                        role,
                        status,
                        page,
                        pageSize
                )
        );
    }

    @PatchMapping("/members/{targetUserId}/role")
    public ResponseEntity<AdminMemberItemResponse> updateMemberRole(
            @PathVariable Long targetUserId,
            @Valid @RequestBody AdminMemberRoleUpdateRequest request
    ) {
        return ResponseEntity.ok(
                adminManagementService.updateMemberRole(
                        targetUserId,
                        request
                )
        );
    }

    @GetMapping("/libraries")
    public ResponseEntity<List<AdminLibraryResponse>> getLibraries(
            @RequestParam Long requesterUserId
    ) {
        return ResponseEntity.ok(
                adminManagementService.getLibraries(requesterUserId)
        );
    }
}
