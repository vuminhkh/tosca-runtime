package com.toscaruntime.exception.deployment.configuration;

import com.toscaruntime.exception.BadUsageException;

/**
 * A target was specified for an IAAS but then the configuration is not found for the target
 */
public class TargetConfigurationNotFoundException extends BadUsageException {

    public TargetConfigurationNotFoundException(String message) {
        super(message);
    }
}
