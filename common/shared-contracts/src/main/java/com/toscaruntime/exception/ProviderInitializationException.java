package com.toscaruntime.exception;

/**
 * Provider cannot be initialized
 */
public class ProviderInitializationException extends DeploymentException {

    public ProviderInitializationException(String message) {
        super(message);
    }

    public ProviderInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
