package com.example.teamproject1.book.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExternalLibraryResponse {

    private String libCode;
    private String libName;
    private String address;
    private String tel;
    private String fax;
    private String latitude;
    private String longitude;
    private String homepage;
    private String closed;
    private String operatingTime;
    private Integer bookCount;
}