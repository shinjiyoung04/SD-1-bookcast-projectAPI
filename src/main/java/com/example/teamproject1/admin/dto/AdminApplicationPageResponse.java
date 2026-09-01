package com.example.teamproject1.admin.dto;

import java.util.List;

public record AdminApplicationPageResponse(
        List<AdminApplicationItemResponse> content,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {
}
