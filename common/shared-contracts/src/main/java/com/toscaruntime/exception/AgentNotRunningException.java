package com.toscaruntime.exception;

public class AgentNotRunningException extends DeploymentException {

    public AgentNotRunningException(String message) {
        super(message);
    }

    public AgentNotRunningException(String message, Throwable cause) {
        super(message, cause);
    }
}
