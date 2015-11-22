package com.toscaruntime.runtime;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The deployment class loader will isolate the deployment from the context class loader (which can be Play Framework's class loader).
 * The only classes which will be loaded from the parent are sdk classes
 *
 * @author Minh Khang VU
 */
public class DeploymentClassLoader extends ClassLoader {

    private static final Logger log = LoggerFactory.getLogger(DeploymentClassLoader.class);

    private Set<String> classesToBeLoadedFromParent;

    private ClassLoader contextClassLoader;

    private void initClassesToBeLoaded(String... classesToBeLoadedFromParent) {
        this.classesToBeLoadedFromParent = new HashSet<>();
        for (String classToBeLoaded : classesToBeLoadedFromParent) {
            this.classesToBeLoadedFromParent.add(classToBeLoaded);
        }
    }

    public DeploymentClassLoader(ClassLoader contextClassLoader, String... classesToBeLoadedFromParent) {
        this.contextClassLoader = contextClassLoader;
        initClassesToBeLoaded(classesToBeLoadedFromParent);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Force loading the class from the context class loader
        for (String classToBeLoadedFromParent : classesToBeLoadedFromParent) {
            if (name.startsWith(classToBeLoadedFromParent)) {
                return this.contextClassLoader.loadClass(name);
            }
        }
        // All others classes will be loaded from the child class loader
        throw new ClassNotFoundException(name + " cannot be resolved by deployment class loader");
    }

    public ClassLoader getContextClassLoader() {
        return contextClassLoader;
    }
}
