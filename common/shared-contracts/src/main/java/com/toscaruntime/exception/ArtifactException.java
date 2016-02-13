package com.toscaruntime.exception;

/**
 * Base class for all related artifact exceptions
 *
 * @author Minh Khang VU
 */
public abstract class ArtifactException extends DeploymentException {

    public ArtifactException(String message) {
        super(message);
    }

    public ArtifactException(String message, Throwable cause) {
        super(message, cause);
    }
}
