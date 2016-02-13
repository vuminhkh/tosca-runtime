package com.toscaruntime.exception;

/**
 * When an execution is interrupted
 *
 * @author Minh Khang VU
 */
public class ArtifactInterruptedException extends ArtifactException {

    public ArtifactInterruptedException(String message) {
        super(message);
    }

    public ArtifactInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
