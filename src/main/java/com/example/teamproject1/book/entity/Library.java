package com.example.teamproject1.book.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "libraries")
@Getter
@Setter
@NoArgsConstructor
public class Library {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "library_id")
    private Long libraryId;

    @Column(name = "lib_code", unique = true, length = 20)
    private String libCode;

    @Column(name = "library_name", nullable = false, length = 100)
    private String libraryName;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length =  30)
    private String phone;

    @Column(name = "created_at")
    private LocalDateTime createdAt;


}
