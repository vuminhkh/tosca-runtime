package com.toscaruntime.exception.deployment.artifact;

import com.toscaruntime.exception.ExpectedException;

/**
 * When an execution is interrupted
 *
 * @author Minh Khang VU
 */
public class ArtifactInterruptedException extends ExpectedException {

    public ArtifactInterruptedException(String message) {
        super(message);
    }

    public ArtifactInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
