package com.toscaruntime.exception.deployment.configuration;

import com.toscaruntime.exception.UnexpectedException;

/**
 * Tosca Function is invalid, this exception is unexpected as if the compiler does its job, we won't have this problem
 */
public class IllegalFunctionException extends UnexpectedException {

    public IllegalFunctionException(String message) {
        super(message);
    }
}
