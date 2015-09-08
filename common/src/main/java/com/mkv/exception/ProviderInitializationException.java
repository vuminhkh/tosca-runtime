package com.mkv.exception;

public class ProviderInitializationException extends InitializationException {

    public ProviderInitializationException(String message) {
        super(message);
    }

    public ProviderInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
