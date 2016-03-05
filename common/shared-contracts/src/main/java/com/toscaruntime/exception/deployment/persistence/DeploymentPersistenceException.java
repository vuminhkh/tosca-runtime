package com.toscaruntime.exception.deployment.persistence;

import com.toscaruntime.exception.UnexpectedException;

/**
 * Happens when deployment runtime state cannot be persisted. It generally indicates that it's a bug in our persistence code as the database is embedded and do not involve any remote communication.
 *
 * @author Minh Khang VU
 */
public class DeploymentPersistenceException extends UnexpectedException {

    public DeploymentPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
