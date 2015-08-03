package com.mkv.exception;

/**
 * Exception that happens in Java code generation phase
 * 
 * @author Minh Khang VU
 */
public abstract class GeneratorException extends RuntimeException {

    public GeneratorException(String message) {
        super(message);
    }

    public GeneratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
