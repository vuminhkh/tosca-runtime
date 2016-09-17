package com.toscaruntime.exception.deployment.configuration;

import com.toscaruntime.exception.UnexpectedException;

public class ToscaTypeNotRegisteredException extends UnexpectedException {

    public ToscaTypeNotRegisteredException(String message) {
        super(message);
    }
}
