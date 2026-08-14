package com.bizkredit.sme.exception;

// Thrown when request data violates a business rule (e.g. duplicate registration number)
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
