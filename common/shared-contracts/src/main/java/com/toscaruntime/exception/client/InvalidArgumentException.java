package com.toscaruntime.exception.client;

import com.toscaruntime.exception.BadUsageException;

public class InvalidArgumentException extends BadUsageException {

    public InvalidArgumentException(String message) {
        super(message);
    }
}
