package com.toscaruntime.exception.deployment.execution;

import com.toscaruntime.exception.BadUsageException;

/**
 * No running execution is found to cancel / resume
 *
 * @author Minh Khang VU
 */
public class RunningExecutionNotFound extends BadUsageException {

    public RunningExecutionNotFound(String message) {
        super(message);
    }
}
