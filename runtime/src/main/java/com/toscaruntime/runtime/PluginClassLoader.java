package com.toscaruntime.runtime;

import java.net.URL;
import java.net.URLClassLoader;

public class PluginClassLoader extends URLClassLoader {

    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    /**
     * Override the parent-first resource loading model established by
     * java.lang.Classloader with child-first behavior.
     */
    protected Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class c = findLoadedClass(name);
            // if not loaded, search the local (child) resources
            if (c == null) {
                try {
                    c = findClass(name);
                } catch (ClassNotFoundException cnfe) {
                    // ignore
                }
            }
            // if we could not find it, delegate to parent
            // Note that we don't attempt to catch any ClassNotFoundException
            if (c == null) {
                if (getParent() != null) {
                    c = getParent().loadClass(name);
                } else {
                    c = getSystemClassLoader().loadClass(name);
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    public URL getResource(String name) {
        URL url = findResource(name);
        // if local search failed, delegate to parent
        if (url == null) {
            url = getParent().getResource(name);
        }
        return url;
    }
}
