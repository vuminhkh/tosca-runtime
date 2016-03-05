package com.toscaruntime.exception.deployment.creation;

import com.toscaruntime.exception.BadUsageException;

/**
 * Provider cannot be initialized due to bad configuration
 */
public class ProviderInitializationException extends BadUsageException {

    public ProviderInitializationException(String message) {
        super(message);
    }

    public ProviderInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
