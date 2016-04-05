package com.toscaruntime.exception.deployment.workflow;

import com.toscaruntime.exception.BadUsageException;

public class InvalidWorkflowArgumentException extends BadUsageException {

    public InvalidWorkflowArgumentException(String message) {
        super(message);
    }
}
