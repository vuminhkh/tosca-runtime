package com.toscaruntime.exception;

/**
 * Thrown when workflow operation encounter error in execution
 *
 * @author Minh Khang VU
 */
public class OperationExecutionException extends DeploymentException {

    public OperationExecutionException(String message) {
        super(message);
    }

    public OperationExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
