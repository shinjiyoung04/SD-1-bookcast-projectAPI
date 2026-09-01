package com.example.teamproject1.promotion.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PromotionRequestCreateDTO {
    private Long userId;
    private String libraryName;
    private String libraryCode;
    private String department;
    private String employeeNumber;
    private String contact;
    private String reason;
}
