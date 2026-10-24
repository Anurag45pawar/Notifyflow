package com.notifyflow.exception;

/** Business-rule validation failed, e.g. an EMAIL notification with a non-email recipient (HTTP 400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
