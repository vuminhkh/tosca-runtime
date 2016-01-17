package com.toscaruntime.exception;

/**
 * Exceptions that happen when interacting with docker daemon or toscaruntime proxy
 *
 * @author Minh Khang VU
 */
public class ToscaRuntimeClientException extends ToscaRuntimeException {

    public ToscaRuntimeClientException(String message) {
        super(message);
    }

    public ToscaRuntimeClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
