package com.toscaruntime.exception.compilation;

import com.toscaruntime.exception.BadUsageException;

/**
 * Exceptions that happen in compilation phase which is related to bad usage of Tosca and ToscaRuntime
 */
public class CompilationException extends BadUsageException {

    public CompilationException(String message) {
        super(message);
    }
}
