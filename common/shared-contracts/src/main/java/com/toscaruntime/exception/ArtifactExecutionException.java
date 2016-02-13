package com.toscaruntime.exception;

public class ArtifactExecutionException extends ArtifactException {

    public ArtifactExecutionException(String message) {
        super(message);
    }

    public ArtifactExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
