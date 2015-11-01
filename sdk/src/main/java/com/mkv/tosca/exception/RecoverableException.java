package com.mkv.tosca.exception;

/**
 * A runtime exception which can be recovered
 * 
 * @author Minh Khang VU
 */
public class RecoverableException extends DeploymentException {

    public RecoverableException(String message) {
        super(message);
    }

    public RecoverableException(String message, Throwable cause) {
        super(message, cause);
    }
}
