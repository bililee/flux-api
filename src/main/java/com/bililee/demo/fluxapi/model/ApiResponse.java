package com.bililee.demo.fluxapi.model;


import lombok.Data;

@Data
public class ApiResponse {

    private Integer statusCode;

    private String statusMsg;

    private ApiData Data;
}
