package com.toscaruntime.exception.client;

import com.toscaruntime.exception.BadUsageException;

public class ImageNotFoundException extends BadUsageException {

    public ImageNotFoundException(String message) {
        super(message);
    }
}
