package com.toscaruntime.exception;

/**
 * At runtime, a property was required but not there
 *
 * @author Minh Khang VU
 */
public class PropertyRequiredException extends PropertyAccessException {

    public PropertyRequiredException(String message) {
        super(message);
    }
}
