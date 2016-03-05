package com.toscaruntime.exception.deployment.configuration;

import com.toscaruntime.exception.UnexpectedException;

/**
 * Cannot access to property value of a deployment
 */
public class PropertyAccessException extends UnexpectedException {

    public PropertyAccessException(String message) {
        super(message);
    }
}
