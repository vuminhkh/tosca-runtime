package com.toscaruntime.exception.compilation;

import com.toscaruntime.exception.compilation.CompilationException;

/**
 * CSAR Dependency not found for compilation
 *
 * @author Minh Khang VU
 */
public class DependencyNotFoundException extends CompilationException {

    public DependencyNotFoundException(String message) {
        super(message);
    }
}
