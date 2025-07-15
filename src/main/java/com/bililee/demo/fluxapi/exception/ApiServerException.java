package com.bililee.demo.fluxapi.exception;

// ApiServerException.java
public class ApiServerException extends RuntimeException {
    private final int statusCode;

    public ApiServerException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}