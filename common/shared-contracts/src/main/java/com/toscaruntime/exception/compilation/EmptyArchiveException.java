package com.toscaruntime.exception.compilation;

import com.toscaruntime.exception.compilation.CompilationException;

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
