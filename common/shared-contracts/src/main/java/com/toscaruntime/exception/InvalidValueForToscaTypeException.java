package com.toscaruntime.exception;

/**
 * When a value is invalid for a tosca type is used without verification
 *
 * @author Minh Khang VU
 */
public class InvalidValueForToscaTypeException extends CompilationException {

    public InvalidValueForToscaTypeException(String message) {
        super(message);
    }

    public InvalidValueForToscaTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
