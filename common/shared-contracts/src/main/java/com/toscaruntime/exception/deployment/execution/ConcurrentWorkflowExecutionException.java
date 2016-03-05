package com.toscaruntime.exception.deployment.execution;

import com.toscaruntime.exception.BadUsageException;

public class ConcurrentWorkflowExecutionException extends BadUsageException {

    public ConcurrentWorkflowExecutionException(String message) {
        super(message);
    }
}
