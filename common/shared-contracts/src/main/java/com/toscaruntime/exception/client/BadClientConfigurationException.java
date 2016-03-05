package com.toscaruntime.exception.client;

import com.toscaruntime.exception.BadUsageException;

public class BadClientConfigurationException extends BadUsageException {

    public BadClientConfigurationException(String message) {
        super(message);
    }

}
