package com.toscaruntime.exception.deployment.execution;

import com.toscaruntime.exception.BadUsageException;

/**
 * Provider/ IAAS resources not found, usually it means user has not correctly configured the resources
 */
public class ProviderResourcesNotFoundException extends BadUsageException {

    public ProviderResourcesNotFoundException(String message) {
        super(message);
    }
}
