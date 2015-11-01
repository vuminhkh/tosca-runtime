package com.mkv.tosca.exception;

public class IllegalFunctionException extends NonRecoverableException {

    public IllegalFunctionException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalFunctionException(String message) {
        super(message);
    }
}
