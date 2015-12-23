package com.toscaruntime.exception;

/**
 * CSAR Dependency not found for compilation
 *
 * @author Minh Khang VU
 */
public class DependencyNotFoundException extends NonRecoverableException {

    public DependencyNotFoundException(String message) {
        super(message);
    }
}
