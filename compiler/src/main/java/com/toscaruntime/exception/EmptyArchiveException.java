package com.toscaruntime.exception;

/**
 * No tosca definition found in the archive
 *
 * @author Minh Khang VU
 */
public class EmptyArchiveException extends CompilationException {

    public EmptyArchiveException(String message) {
        super(message);
    }
}
