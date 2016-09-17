package com.toscaruntime.exception.deployment.artifact;

import com.toscaruntime.exception.UnexpectedException;

/**
 * Executor was not correctly configured
 */
public class BadExecutorConfigurationException extends UnexpectedException {

    public BadExecutorConfigurationException(String message) {
        super(message);
    }

    public BadExecutorConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
