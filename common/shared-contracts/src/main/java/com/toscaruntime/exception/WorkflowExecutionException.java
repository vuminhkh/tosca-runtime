package com.toscaruntime.exception;

/**
 * Exception happens in deployment/ runtime that concerns workflow execution failure (script failed etc ...)
 *
 * @author Minh Khang VU
 */
public class WorkflowExecutionException extends DeploymentException {

    public WorkflowExecutionException(String message) {
        super(message);
    }

    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
