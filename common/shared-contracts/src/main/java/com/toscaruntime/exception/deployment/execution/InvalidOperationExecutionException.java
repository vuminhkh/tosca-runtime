package com.toscaruntime.exception.deployment.execution;

import com.toscaruntime.exception.BadUsageException;

/**
 * Thrown when workflow operation encounter error in execution due to the fact that it was launched in a bad manner (start a node but it has not been created yet for example)
 *
 * @author Minh Khang VU
 */
public class InvalidOperationExecutionException extends BadUsageException {

    public InvalidOperationExecutionException(String message) {
        super(message);
    }

    public InvalidOperationExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
