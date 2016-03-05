package com.toscaruntime.exception.client;

import com.toscaruntime.exception.BadUsageException;

public class AgentNotRunningException extends BadUsageException {

    public AgentNotRunningException(String message) {
        super(message);
    }
}
