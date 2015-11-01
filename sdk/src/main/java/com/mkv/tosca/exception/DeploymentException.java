package com.mkv.tosca.exception;

/**
 * Exception that happens in deployment/runtime phase
 * 
 * @author Minh Khang VU
 */
public abstract class DeploymentException extends RuntimeException {

    public DeploymentException(String message) {
        super(message);
    }

    public DeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
