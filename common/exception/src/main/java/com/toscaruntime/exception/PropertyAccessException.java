package com.toscaruntime.exception;

public class PropertyAccessException extends NonRecoverableException {

    public PropertyAccessException(String message) {
        super(message);
    }

    public PropertyAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
