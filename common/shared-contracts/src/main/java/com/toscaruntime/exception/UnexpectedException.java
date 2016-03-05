package com.toscaruntime.exception;

/**
 * Unexpected behaviour inside tosca runtime, when it's thrown it's in general indicating a bug.
 * When this exception happens, generally there's nothing more to do other than log the error and inform the user.
 *
 * @author Minh Khang VU
 */
public class UnexpectedException extends RuntimeException {

    public UnexpectedException(String message) {
        super(message);
    }

    public UnexpectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
