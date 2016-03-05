package com.toscaruntime.exception.deployment.artifact;

import com.toscaruntime.exception.BadUsageException;

public class ArtifactExecutionException extends BadUsageException {

    public ArtifactExecutionException(String message) {
        super(message);
    }

    public ArtifactExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
