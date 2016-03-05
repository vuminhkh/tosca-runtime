package com.toscaruntime.exception.client;

import com.toscaruntime.exception.BadUsageException;

/**
 * Bad client request
 */
public class BadRequestException extends BadUsageException {

    public BadRequestException(String message) {
        super(message);
    }
}
