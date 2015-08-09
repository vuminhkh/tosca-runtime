package com.mkv.exception;

/**
 * Exception that happens in Java code generation phase
 * 
 * @author Minh Khang VU
 */
public abstract class GenerationException extends RuntimeException {

    public GenerationException(String message) {
        super(message);
    }

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
