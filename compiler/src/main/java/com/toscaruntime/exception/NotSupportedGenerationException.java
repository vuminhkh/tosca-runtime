package com.toscaruntime.exception;

public class NotSupportedGenerationException extends GenerationException {

    public NotSupportedGenerationException(String message) {
        super(message);
    }

    public NotSupportedGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
