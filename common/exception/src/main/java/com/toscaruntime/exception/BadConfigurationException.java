package com.toscaruntime.exception;

public class BadConfigurationException extends NonRecoverableException {

    public BadConfigurationException(String message) {
        super(message);
    }

    public BadConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
