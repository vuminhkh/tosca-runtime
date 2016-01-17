package com.toscaruntime.exception;

/**
 * Provider/ IAAS resources not found
 */
public class ProviderResourcesNotFoundException extends DeploymentException {

    public ProviderResourcesNotFoundException(String message) {
        super(message);
    }
}
