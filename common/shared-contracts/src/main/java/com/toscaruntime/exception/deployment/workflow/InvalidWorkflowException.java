package com.toscaruntime.exception.deployment.workflow;

import com.toscaruntime.exception.BadUsageException;

public class InvalidWorkflowException extends BadUsageException {

    public InvalidWorkflowException(String message) {
        super(message);
    }

    public InvalidWorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
