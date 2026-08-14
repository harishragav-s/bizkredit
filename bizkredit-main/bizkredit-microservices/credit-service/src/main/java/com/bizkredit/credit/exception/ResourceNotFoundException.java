package com.bizkredit.credit.exception;

// Thrown when a requested resource is not found in the database
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
