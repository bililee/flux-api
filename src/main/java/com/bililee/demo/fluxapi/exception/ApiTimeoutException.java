package com.bililee.demo.fluxapi.exception;

// ApiTimeoutException.java
public class ApiTimeoutException extends RuntimeException {
    public ApiTimeoutException(String message) {
        super(message);
    }
}
