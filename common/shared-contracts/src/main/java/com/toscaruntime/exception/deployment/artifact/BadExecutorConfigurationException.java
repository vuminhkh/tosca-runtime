package com.toscaruntime.exception.deployment.artifact;

import com.toscaruntime.exception.BadUsageException;

/**
 * Executor was not correctly configured
 */
public class BadExecutorConfigurationException extends BadUsageException {

    public BadExecutorConfigurationException(String message) {
        super(message);
    }

    public BadExecutorConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
