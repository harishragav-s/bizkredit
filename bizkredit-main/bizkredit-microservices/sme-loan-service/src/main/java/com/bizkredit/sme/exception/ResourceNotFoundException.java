package com.bizkredit.sme.exception;

// Thrown when a requested resource is not found in the database
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
