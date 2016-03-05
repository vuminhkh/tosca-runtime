package com.toscaruntime.exception.compilation;

/**
 * Exception that happens in Java code generation phase
 *
 * @author Minh Khang VU
 */
public abstract class GenerationException extends CompilationException {

    public GenerationException(String message) {
        super(message);
    }
}
