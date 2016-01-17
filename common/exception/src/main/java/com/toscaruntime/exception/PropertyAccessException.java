package com.toscaruntime.exception;

/**
 * Cannot access to property value of a deployment
 */
public class PropertyAccessException extends DeploymentException {

    public PropertyAccessException(String message) {
        super(message);
    }

    public PropertyAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
