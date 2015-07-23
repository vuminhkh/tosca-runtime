package com.mkv.exception;

/**
 * A runtime exception which can not be recovered
 * 
 * @author Minh Khang VU
 */
public class NonRecoverableException extends RuntimeException {

    public NonRecoverableException(String message) {
        super(message);
    }

    public NonRecoverableException(String message, Throwable cause) {
        super(message, cause);
    }
}
