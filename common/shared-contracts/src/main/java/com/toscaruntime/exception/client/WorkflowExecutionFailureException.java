package com.toscaruntime.exception.client;

import com.toscaruntime.exception.BadUsageException;

public class WorkflowExecutionFailureException extends BadUsageException {

    public WorkflowExecutionFailureException(String message) {
        super(message);
    }
}
