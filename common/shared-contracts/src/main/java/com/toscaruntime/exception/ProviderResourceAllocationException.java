package com.toscaruntime.exception;

/**
 * Happens when provider resource cannot be allocated
 *
 * @author Minh Khang VU
 */
public class ProviderResourceAllocationException extends DeploymentException {

    public ProviderResourceAllocationException(String message) {
        super(message);
    }

    public ProviderResourceAllocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
