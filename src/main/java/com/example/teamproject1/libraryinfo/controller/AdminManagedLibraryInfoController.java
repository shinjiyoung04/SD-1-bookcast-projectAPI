package com.example.teamproject1.libraryinfo.controller;

import com.example.teamproject1.libraryinfo.dto.ManagedLibraryInfoResponse;
import com.example.teamproject1.libraryinfo.service.AdminManagedLibraryInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminManagedLibraryInfoController {

    private final AdminManagedLibraryInfoService
            adminManagedLibraryInfoService;

    @GetMapping("/managed-library-info")
    public ResponseEntity<ManagedLibraryInfoResponse>
    getManagedLibraryInfo(
            @RequestParam Long requesterUserId,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return ResponseEntity.ok(
                adminManagedLibraryInfoService
                        .getManagedLibraryInfo(
                                requesterUserId,
                                refresh
                        )
        );
    }
}
