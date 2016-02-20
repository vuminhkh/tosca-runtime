package com.toscaruntime.exception;

public class AgentDownException extends DeploymentException {

    public AgentDownException(String message) {
        super(message);
    }

    public AgentDownException(String message, Throwable cause) {
        super(message, cause);
    }
}
