package com.toscaruntime.exception.deployment.artifact;

import com.toscaruntime.exception.BadUsageException;

public class ArtifactNotSupportedException extends BadUsageException {

    public ArtifactNotSupportedException(String message) {
        super(message);
    }
}
