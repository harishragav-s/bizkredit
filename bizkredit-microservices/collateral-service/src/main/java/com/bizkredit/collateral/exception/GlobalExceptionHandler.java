package com.bizkredit.collateral.exception;

import com.bizkredit.collateral.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handle(Exception ex, HttpServletRequest request) {
        // Includes the HTTP method/path and the caller's identity+roles so an
        // "Access Denied" or similar failure can actually be traced to which
        // endpoint and which role attempted it, instead of just a bare exception
        // name with no context.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String caller = auth != null ? auth.getName() + " " + auth.getAuthorities() : "unauthenticated";
        log.error("Exception caught: {} - {} | {} {} | caller={}",
                ex.getClass().getSimpleName(), ex.getMessage(),
                request.getMethod(), request.getRequestURI(), caller);


        return switch (ex) {
            case ResourceNotFoundException e ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));

            case BadRequestException e ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

            case ForbiddenException e ->
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(e.getMessage()));

            case UnauthorizedException e ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));

            case BadCredentialsException e ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid email or password"));

            case LockedException e ->
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Account is locked. Contact admin."));

            case DisabledException e ->
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Account is inactive. Contact admin."));

            case AccessDeniedException e ->
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access denied: insufficient permissions"));

            case MethodArgumentNotValidException e -> {
                String errors = e.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
                yield ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Validation failed: " + errors));
            }

            default -> {
                log.error("Unhandled exception", ex);
                String detail = ex.getClass().getSimpleName()
                        + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
                yield ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Internal server error - " + detail));
            }
        };
    }
}
