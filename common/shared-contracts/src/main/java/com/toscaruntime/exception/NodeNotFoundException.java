package com.toscaruntime.exception;

public class NodeNotFoundException extends DeploymentException {

    public NodeNotFoundException(String message) {
        super(message);
    }

    public NodeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
