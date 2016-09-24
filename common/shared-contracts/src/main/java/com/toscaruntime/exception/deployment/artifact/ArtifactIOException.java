package com.toscaruntime.exception.deployment.artifact;

import com.toscaruntime.exception.ThirdPartyException;

public class ArtifactIOException extends ThirdPartyException {

    public ArtifactIOException(String message) {
        super(message);
    }

    public ArtifactIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
