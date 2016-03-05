package com.toscaruntime.exception.deployment.creation;

import com.toscaruntime.exception.UnexpectedException;

/**
 * Deployment is in an incoherent state, that makes impossible its creation, this usually means bug
 *
 * @author Minh Khang VU
 */
public class InvalidDeploymentStateException extends UnexpectedException {

    public InvalidDeploymentStateException(String message) {
        super(message);
    }

    public InvalidDeploymentStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
