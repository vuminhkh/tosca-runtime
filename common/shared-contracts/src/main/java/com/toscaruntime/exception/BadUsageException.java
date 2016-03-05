package com.toscaruntime.exception;

/**
 * Exception caused by bad usage of user, can be bad configuration, bad input, bad recipe.
 * When this exception happens, ToscaRuntime should inform the user and do not process further the request.
 *
 * @author Minh Khang VU
 */
public abstract class BadUsageException extends RuntimeException {
    public BadUsageException(String message) {
        super(message);
    }

    public BadUsageException(String message, Throwable cause) {
        super(message, cause);
    }
}
