package com.toscaruntime.exception;

/**
 * @author Minh Khang VU
 */
public class ArtifactConnectException extends ArtifactException {

    public ArtifactConnectException(String message) {
        super(message);
    }

    public ArtifactConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}
