package com.example.teamproject1.book.dto;

public class LibraryResponseDTO {

    private String libCode;
    private String libName;
    private String address;
    private String tel;
    private String homepage;
    private String closed;
    private String operatingTime;

    public LibraryResponseDTO() {
    }

    public LibraryResponseDTO(
            String libCode,
            String libName,
            String address,
            String tel,
            String homepage,
            String closed,
            String operatingTime
    ) {
        this.libCode = libCode;
        this.libName = libName;
        this.address = address;
        this.tel = tel;
        this.homepage = homepage;
        this.closed = closed;
        this.operatingTime = operatingTime;
    }

    public String getLibCode() {
        return libCode;
    }

    public void setLibCode(String libCode) {
        this.libCode = libCode;
    }

    public String getLibName() {
        return libName;
    }

    public void setLibName(String libName) {
        this.libName = libName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTel() {
        return tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public String getHomepage() {
        return homepage;
    }

    public void setHomepage(String homepage) {
        this.homepage = homepage;
    }

    public String getClosed() {
        return closed;
    }

    public void setClosed(String closed) {
        this.closed = closed;
    }

    public String getOperatingTime() {
        return operatingTime;
    }

    public void setOperatingTime(String operatingTime) {
        this.operatingTime = operatingTime;
    }
}