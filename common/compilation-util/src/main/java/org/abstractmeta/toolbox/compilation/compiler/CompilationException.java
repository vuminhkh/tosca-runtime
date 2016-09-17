package org.abstractmeta.toolbox.compilation.compiler;

import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

public class CompilationException extends Exception {

    private List<Diagnostic<? extends JavaFileObject>> errors;

    public CompilationException(List<Diagnostic<? extends JavaFileObject>> errors) {
        this.errors = errors;
    }
}
