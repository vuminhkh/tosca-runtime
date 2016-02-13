package com.toscaruntime.exception;

public class BadClientConfigurationException extends ToscaRuntimeClientException {

    public BadClientConfigurationException(String message) {
        super(message);
    }

    public BadClientConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
