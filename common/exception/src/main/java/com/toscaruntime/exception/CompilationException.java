package com.toscaruntime.exception;

/**
 * Exceptions that happen in compilation phase
 */
public class CompilationException extends ToscaRuntimeException {

    public CompilationException(String message) {
        super(message);
    }

    public CompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
