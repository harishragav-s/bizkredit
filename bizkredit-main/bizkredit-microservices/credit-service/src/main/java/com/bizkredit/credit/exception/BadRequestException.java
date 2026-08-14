package com.bizkredit.credit.exception;

// Thrown when request data violates a business rule
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
