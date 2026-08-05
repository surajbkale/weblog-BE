package com.weblogs.blog.exception;

/**
 * Thrown when an authenticated user attempts an action they are not permitted to perform
 * (e.g. editing another user's post). Maps to HTTP 403 via GlobalExceptionHandler.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
