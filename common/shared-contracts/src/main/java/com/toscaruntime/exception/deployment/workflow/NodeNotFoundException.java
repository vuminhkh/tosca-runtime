package com.toscaruntime.exception.deployment.workflow;

public class NodeNotFoundException extends InvalidWorkflowArgumentException {

    public NodeNotFoundException(String message) {
        super(message);
    }
}
