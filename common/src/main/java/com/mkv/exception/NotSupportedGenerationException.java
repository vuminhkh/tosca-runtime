package com.mkv.exception;

public class NotSupportedGenerationException extends GeneratorException {

    public NotSupportedGenerationException(String message) {
        super(message);
    }

    public NotSupportedGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
