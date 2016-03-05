package com.toscaruntime.exception;

/**
 * Exception that is thrown for internal ToscaRuntime logic.
 * For example, it can be a wrapper for a InterruptedException to stop thread's execution etc ...
 * When this exception happens, generally as it's expected, it should be handled internally in ToscaRuntime and not propagated to the end user.
 *
 * @author Minh Khang VU
 */
public abstract class ExpectedException extends RuntimeException {
    public ExpectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExpectedException(String message) {
        super(message);
    }
}
