package com.toscaruntime.exception;

public class InterruptedByUserException extends ExpectedException {

    public InterruptedByUserException(String message, Throwable cause) {
        super(message, cause);
    }

    public InterruptedByUserException(String message) {
        super(message);
    }
}
