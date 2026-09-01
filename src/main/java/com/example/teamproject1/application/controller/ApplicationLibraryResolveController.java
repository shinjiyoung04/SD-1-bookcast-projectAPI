package com.example.teamproject1.application.controller;

import com.example.teamproject1.application.dto.ApplicationLibraryResolveRequest;
import com.example.teamproject1.application.dto.ApplicationLibraryResolveResponse;
import com.example.teamproject1.application.service.ApplicationLibraryResolveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        "/api/application-libraries"
)
public class ApplicationLibraryResolveController {

    private final ApplicationLibraryResolveService
            applicationLibraryResolveService;

    @PostMapping("/resolve")
    public ResponseEntity<ApplicationLibraryResolveResponse>
    resolveLibrary(
            @Valid
            @RequestBody
            ApplicationLibraryResolveRequest request
    ) {
        return ResponseEntity.ok(
                applicationLibraryResolveService
                        .resolve(request)
        );
    }
}