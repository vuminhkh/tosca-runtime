package com.toscaruntime.exception.deployment.artifact;

import com.toscaruntime.exception.BadUsageException;

public class ArtifactExecutorNotFoundException extends BadUsageException {

    public ArtifactExecutorNotFoundException(String message) {
        super(message);
    }

    public ArtifactExecutorNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
