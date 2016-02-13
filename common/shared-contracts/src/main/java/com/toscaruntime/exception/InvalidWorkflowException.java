package com.toscaruntime.exception;

public class InvalidWorkflowException extends DeploymentException {

    public InvalidWorkflowException(String message) {
        super(message);
    }

    public InvalidWorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
