/**
 * Copyright 2011 Adrian Witas
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.abstractmeta.toolbox.compilation.compiler.impl;


import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.abstractmeta.toolbox.compilation.compiler.CompilationException;
import org.abstractmeta.toolbox.compilation.compiler.JavaSourceCompiler;
import org.abstractmeta.toolbox.compilation.compiler.registry.JavaFileObjectRegistry;
import org.abstractmeta.toolbox.compilation.compiler.registry.impl.JavaFileObjectRegistryImpl;
import org.abstractmeta.toolbox.compilation.compiler.util.ClassPathUtil;
import org.abstractmeta.toolbox.compilation.compiler.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides implementation of JavaSourceCompiler interface.
 * This implementation uses {@link JavaCompiler}.
 * <p><b>Usage:</b>
 * <code><pre>
 * JavaSourceCompiler javaSourceCompiler = new JavaSourceCompilerImpl();
 * JavaSourceCompiler.CompilationUnit compilationUnit = javaSourceCompiler.createCompilationUnit();
 * compilationUnit.addJavaSource("com.test.foo.Foo","package com.test.foo;" +
 *        "public class Foo {\n" +
 *        "        public static void main(String [] args) {\n" +
 *        "            System.out.println(\"Hello world\");\n" +
 *        "        }\n" +
 *        "}");
 * ClassLoader classLoader = javaSourceCompiler.compile(compilationUnit);
 * Class clazz = classLoader.loadClass("com.test.foo.Foo");
 * Object foo = clazz.newInstance();
 * Method main = clazz.getMethod("main", String[].class);
 * String args = null;
 * main.invoke(foo, args);
 * </pre></code>
 * </p>
 * <p/>
 * <p/>
 * <p><i>Note</i> that to be able to use java compiler you will have to add tools.jar to your class path.
 * </p>
 *
 * @author Adrian Witas
 */

public class JavaSourceCompilerImpl implements JavaSourceCompiler {

    private static Logger logger = LoggerFactory.getLogger(JavaSourceCompilerImpl.class);

    private static final List<String> CLASS_PATH_OPTIONS = new ArrayList<String>(Arrays.asList("cp", "classpath"));
    private static final String CLASS_PATH_DELIMITER = ClassPathUtil.getClassPathSeparator();

    @Override
    public ClassLoader compile(CompilationUnit compilationUnit, String... options) throws CompilationException {
        return compile(this.getClass().getClassLoader(), compilationUnit, options);
    }

    @Override
    public ClassLoader compile(ClassLoader parentClassLoader, CompilationUnit compilationUnit, String... options) throws CompilationException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        return compile(compiler, parentClassLoader, compilationUnit, options);
    }


    protected ClassLoader compile(JavaCompiler compiler, ClassLoader parentClassLoader, CompilationUnit compilationUnit, String... options) throws CompilationException {
        if (compiler == null) {
            throw new IllegalStateException("Failed to find the system Java compiler. Check that your class path includes tools.jar");
        }
        JavaFileObjectRegistry registry = compilationUnit.getRegistry();
        SimpleClassLoader result = new SimpleClassLoader(parentClassLoader, registry, compilationUnit.getOutputClassDirectory());
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostics, null, null);
        JavaFileManager javaFileManager = new SimpleJavaFileManager(standardFileManager, result, registry);
        Iterable<JavaFileObject> sources = registry.get(JavaFileObject.Kind.SOURCE);
        Collection<String> compilationOptions = buildOptions(compilationUnit, result, options);
        JavaCompiler.CompilationTask task = compiler.getTask(null, javaFileManager, diagnostics, compilationOptions, null, sources);
        task.call();
        List<Diagnostic<? extends JavaFileObject>> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind().equals(Diagnostic.Kind.ERROR)) {
                errors.add(diagnostic);
                logger.error(diagnostic.toString());
            } else {
                logger.warn(diagnostic.toString());
            }
        }
        if (!errors.isEmpty()) {
            throw new CompilationException(diagnostics.getDiagnostics());
        }
        result.addClassPathEntries(compilationUnit.getClassPathsEntries());
        return result;
    }

    protected Collection<String> buildOptions(CompilationUnit compilationUnit, SimpleClassLoader classLoader, String... options) {
        List<String> result = new ArrayList<String>();
        Map<String, String> optionsMap = new HashMap<String, String>();
        for (int i = 0; i < options.length; i += 2) {
            optionsMap.put(options[i], options[i + 1]);
        }
        for (String classPathKey : CLASS_PATH_OPTIONS) {
            if (optionsMap.containsKey(classPathKey)) {
                addClassPath(compilationUnit, optionsMap.get(classPathKey));
            }
        }
        for (String key : optionsMap.keySet()) {
            if (CLASS_PATH_OPTIONS.contains(key)) {
                continue;
            }
            result.addAll(Arrays.asList(key, optionsMap.get(key)));
        }
        addClassPath(result, compilationUnit);

        return result;
    }

    /**
     * Adds given class path entries of compilation unit to the supplied option result list.
     * This method simply add -cp 'cass_path_entry1:...:clas_path_entry_x' options
     *
     * @param result          result list
     * @param compilationUnit compilation unit
     */
    private void addClassPath(List<String> result, CompilationUnit compilationUnit) {
        StringBuilder classPathBuilder = new StringBuilder();
        for (String entry : compilationUnit.getClassPathsEntries()) {
            if (classPathBuilder.length() > 0) {
                classPathBuilder.append(CLASS_PATH_DELIMITER);
            }
            classPathBuilder.append(entry);
        }
        if (classPathBuilder.length() > 0) {
            result.addAll(Arrays.asList("-cp", classPathBuilder.toString()));
        }
    }


    protected void addClassPath(CompilationUnit result, String classPath) {
        String[] classPathEntries = classPath.split(CLASS_PATH_DELIMITER);

        for (String classPathEntry : classPathEntries) {
            result.addClassPathEntry(classPathEntry);
        }
    }


    @Override
    public CompilationUnit createCompilationUnit() {
        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), "compiled-code_" + System.currentTimeMillis());
        return createCompilationUnit(outputDirectory);
    }

    @Override
    public CompilationUnit createCompilationUnit(File outputClassDirectory) {
        return new CompilationUnitImpl(outputClassDirectory);
    }


    public void persistCompiledClasses(CompilationUnit compilationUnit) {
        JavaFileObjectRegistry registry = compilationUnit.getRegistry();
        File classOutputDirectory = compilationUnit.getOutputClassDirectory();
        if (!classOutputDirectory.exists()) {
            if (!classOutputDirectory.mkdirs()) throw new IllegalStateException("Failed to create directory " + classOutputDirectory.getAbsolutePath());
        }
        for (JavaFileObject javaFileObject : registry.get(JavaFileObject.Kind.CLASS)) {
            String internalName = javaFileObject.getName().substring(1);
            File compiledClassFile = new File(classOutputDirectory, internalName);
            if (!compiledClassFile.getParentFile().exists()) {
                if (!compiledClassFile.getParentFile().mkdirs()) {
                    throw new IllegalStateException("Failed to create directories " + compiledClassFile.getParent());
                }
            }
            try {
                Files.write(compiledClassFile.toPath(), JavaCodeFileObject.class.cast(javaFileObject).getByteCode());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write to file " + compiledClassFile, e);
            }
        }
    }

    public static class CompilationUnitImpl implements CompilationUnit {

        private final List<String> classPathEntries = new ArrayList<String>();
        private final JavaFileObjectRegistry registry = new JavaFileObjectRegistryImpl();
        private final File outputClassDirectory;

        public CompilationUnitImpl(File outputClassDirectory) {
            this.outputClassDirectory = outputClassDirectory;
        }

        @Override
        public void addClassPathEntry(String classPathEntry) {
            classPathEntries.add(classPathEntry);
        }

        @Override
        public void addClassPathEntries(Collection<String> classPathEntries) {
            this.classPathEntries.addAll(classPathEntries);
        }

        @Override
        public void addJavaSource(String className, String source) {
            URI sourceUri = URIUtil.buildUri(StandardLocation.SOURCE_OUTPUT, className);
            registry.register(new JavaSourceFileObject(sourceUri, source));
        }

        @Override
        public JavaFileObjectRegistry getRegistry() {
            return registry;
        }

        @Override
        public List<String> getClassPathsEntries() {
            return classPathEntries;
        }

        public File getOutputClassDirectory() {
            return outputClassDirectory;
        }
    }

}
