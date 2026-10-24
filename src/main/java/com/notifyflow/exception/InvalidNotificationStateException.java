package com.notifyflow.exception;

/** The requested action is not allowed in the notification's current status (HTTP 409). */
public class InvalidNotificationStateException extends RuntimeException {

    public InvalidNotificationStateException(String message) {
        super(message);
    }
}
