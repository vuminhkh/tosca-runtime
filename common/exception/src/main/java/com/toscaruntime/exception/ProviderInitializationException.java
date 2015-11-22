package com.toscaruntime.exception;

public class ProviderInitializationException extends NonRecoverableException {

    public ProviderInitializationException(String message) {
        super(message);
    }

    public ProviderInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
