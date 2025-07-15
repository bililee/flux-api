package com.bililee.demo.fluxapi.model;


import lombok.Data;

@Data
public class RestApiResult {

    private Integer statusCode;

    private String statusMsg;

    private ApiResult apiResult;
}
