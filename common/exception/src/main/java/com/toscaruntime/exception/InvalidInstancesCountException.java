package com.toscaruntime.exception;

public class InvalidInstancesCountException extends DeploymentException {

    public InvalidInstancesCountException(String message) {
        super(message);
    }

    public InvalidInstancesCountException(String message, Throwable cause) {
        super(message, cause);
    }
}
