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


import org.abstractmeta.toolbox.compilation.compiler.registry.JavaFileObjectRegistry;
import org.abstractmeta.toolbox.compilation.compiler.util.URIUtil;
import org.slf4j.LoggerFactory;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * <p>This implementation uses the following mechanism to lookup requested class.
 * <ul>
 * <li>java object registry: {@link JavaFileObjectRegistry}</li>
 * <li>jar files class path entries</li>
 * <li>directory class path entries</li>
 * </ul>
 * Note that to be able find use  {@link SimpleClassLoader#findResource(String)} or  {@link SimpleClassLoader#findResources(String)} for registry resources (classes compiled in memory),
 * please use {@link JavaSourceCompilerImpl#persistCompiledClasses(org.abstractmeta.toolbox.compilation.compiler.JavaSourceCompiler.CompilationUnit)}
 * to persist compiled classes.
 * </p>
 *
 * @author Adrian Witas
 */

public class SimpleClassLoader extends ClassLoader {

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(SimpleClassLoader.class);

    private final List<JarFile> jarFiles = new ArrayList<JarFile>();
    private final List<File> classDirectories = new ArrayList<File>();
    private final JavaFileObjectRegistry registry;
    private final Path classOutputDirectory;

    public SimpleClassLoader(final ClassLoader parentClassLoader, JavaFileObjectRegistry registry, Path classOutputDirectory) {
        super(parentClassLoader);
        this.registry = registry;
        this.classOutputDirectory = classOutputDirectory;
    }

    public void addClassPathEntry(String classPathEntry) {

        if (classPathEntry.endsWith(".jar")) {
            try {
                JarFile jarFile = new JarFile(classPathEntry);
                jarFiles.add(jarFile);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to register classPath entry: " + classPathEntry, e);
            }

        } else {
            File classDirectory = new File(classPathEntry);
            classDirectories.add(classDirectory);
        }
    }


    public void addClassPathEntries(Iterable<String> classPathEntries) {
        for (String classPathEntry : classPathEntries) {
            addClassPathEntry(classPathEntry);
        }
    }

    @Override
    protected Class<?> findClass(final String qualifiedClassName) throws ClassNotFoundException {
        URI classUri = URIUtil.buildUri(StandardLocation.CLASS_OUTPUT, qualifiedClassName);
        if (registry.isRegistered(classUri)) {
            JavaFileObject result = registry.get(classUri);
            byte[] byteCode = JavaCodeFileObject.class.cast(result).getByteCode();
            return defineClass(qualifiedClassName, byteCode, 0, byteCode.length);
        }

        Class<?> result = findClassInFileSystem(qualifiedClassName);
        if (result != null) {
            return result;
        }
        result = findClassInJarFile(qualifiedClassName);
        if (result != null) {
            return result;
        }
        try {
            result = Class.forName(qualifiedClassName);
            return result;
        } catch (ClassNotFoundException nf) {
            // Ignore and fall through
        }
        return super.findClass(qualifiedClassName);
    }

    protected Class<?> findClassInFileSystem(String qualifiedClassName) {
        for (File classDirectory : classDirectories) {
            File classFile = new File(classDirectory, qualifiedClassName.replace('.', '/') + ".class");
            if (classFile.exists()) {
                try {
                    byte[] byteCode = Files.readAllBytes(classFile.toPath());
                    return defineClass(qualifiedClassName, byteCode, 0, byteCode.length);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read class file " + classFile, e);
                }
            }
        }
        return null;
    }


    protected Class<?> findClassInJarFile(String qualifiedClassName) throws ClassNotFoundException {
        URI classUri = URIUtil.buildUri(StandardLocation.CLASS_OUTPUT, qualifiedClassName);
        String internalClassName = classUri.getPath().substring(1);
        JarFile jarFile = null;
        try {
            for (int i = 0; i < jarFiles.size(); i++) {
                jarFile = jarFiles.get(i);
                JarEntry jarEntry = jarFile.getJarEntry(internalClassName);
                if (jarEntry != null) {
                    try (DataInputStream inputStream = new DataInputStream(jarFile.getInputStream(jarEntry))) {
                        byte[] byteCode = new byte[(int) jarEntry.getSize()];
                        inputStream.readFully(byteCode);
                        return defineClass(qualifiedClassName, byteCode, 0, byteCode.length);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Failed to lookup class %s in jar file %s", qualifiedClassName, jarFile), e);
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String resource) throws IOException {
        List<URL> result = new ArrayList<URL>(Collections.list(super.findResources(resource)));
        findResourcesInJarFiles(result, resource);
        findResourcesInJavaFileObjectRegistry(result, resource);
        return Collections.enumeration(result);
    }

    @Override
    protected URL findResource(String name) {
        try {
            URL resource = super.findResource(name);
            if (resource == null) {
                resource = findResourceInJarFiles(name);
            }
            if (resource == null) {
                resource = findResourceInJavaFileObjectRegistry(name);
            }
            return resource;
        } catch (IOException e) {
            logger.warn("Unable to load resource <" + name + "> error " + e.getMessage());
            return null;
        }
    }

    protected void findResourcesInJarFiles(List<URL> result, String resource) throws MalformedURLException {
        for (JarFile jarFile : jarFiles) {
            JarEntry entry = jarFile.getJarEntry(resource);
            if (entry != null) {
                result.add(new URL("jar", "", String.format("file:%s!/%s", jarFile.getName(), resource)));
            }
        }
    }

    protected URL findResourceInJarFiles(String resource) throws MalformedURLException {
        for (JarFile jarFile : jarFiles) {
            JarEntry entry = jarFile.getJarEntry(resource);
            if (entry != null) {
                return new URL("jar", "", String.format("file:%s!/%s", jarFile.getName(), resource));
            }
        }
        return null;
    }


    protected void findResourcesInJavaFileObjectRegistry(List<URL> result, String resource) throws MalformedURLException {
        for (JavaFileObject javaFileObject : registry.get(JavaFileObject.Kind.CLASS)) {
            String internalName = javaFileObject.getName().substring(1);
            if (internalName.startsWith(resource)) {
                Path file = classOutputDirectory.resolve(internalName);
                result.add(new URL(String.format("file://%s", file.toAbsolutePath().toString())));
            }
        }
    }

    protected URL findResourceInJavaFileObjectRegistry(String resource) throws MalformedURLException {
        for (JavaFileObject javaFileObject : registry.get(JavaFileObject.Kind.CLASS)) {
            String internalName = javaFileObject.getName().substring(1);
            if (internalName.startsWith(resource)) {
                Path file = classOutputDirectory.resolve(internalName);
                return new URL(String.format("file://%s", file.toAbsolutePath().toString()));
            }
        }
        return null;
    }

    public URL getResource(String name) {
        URL url = super.getResource(name);
        if (url == null) {
            if (getParent() != null) {
                url = getParent().getResource(name);
            } else {
                url = findResource(name);
            }
        }
        return url;
    }
}