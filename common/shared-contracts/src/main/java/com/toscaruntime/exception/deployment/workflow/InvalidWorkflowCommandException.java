package com.toscaruntime.exception.deployment.workflow;

import com.toscaruntime.exception.BadUsageException;

public class InvalidWorkflowCommandException extends BadUsageException {
    public InvalidWorkflowCommandException(String message) {
        super(message);
    }
}
