package com.toscaruntime.exception;

/**
 * Exception that happens in Java code generation phase
 *
 * @author Minh Khang VU
 */
public abstract class GenerationException extends CompilationException {

    public GenerationException(String message) {
        super(message);
    }

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
