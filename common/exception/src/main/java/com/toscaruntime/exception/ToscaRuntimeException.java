package com.toscaruntime.exception;

/**
 * A runtime exception which can not be recovered
 * 
 * @author Minh Khang VU
 */
public class ToscaRuntimeException extends DeploymentException {

    public ToscaRuntimeException(String message) {
        super(message);
    }

    public ToscaRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
