package com.toscaruntime.exception;

/**
 * Tosca Function is invalid
 */
public class IllegalFunctionException extends DeploymentException {

    public IllegalFunctionException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalFunctionException(String message) {
        super(message);
    }
}
