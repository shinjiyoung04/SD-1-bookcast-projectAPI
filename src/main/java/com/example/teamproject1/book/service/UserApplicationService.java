package com.example.teamproject1.book.service;

import com.example.teamproject1.book.dto.UserApplicationListResponse;
import com.example.teamproject1.book.repository.HopeApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.w3c.dom.stylesheets.LinkStyle;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private final HopeApplicationRepository hopeApplicationRepository;

    public List<UserApplicationListResponse> getMyApplications(Long userId){
        return hopeApplicationRepository.findUserApplicationList(userId);
    }
}
