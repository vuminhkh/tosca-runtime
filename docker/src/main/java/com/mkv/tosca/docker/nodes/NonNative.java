package com.mkv.tosca.docker.nodes;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tosca.nodes.Root;

/**
 * This is the base class for all non native node. It describes how operations should be executed.
 * 
 * @author Minh Khang VU
 */
public abstract class NonNative extends Root {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    private Container hostContainer;

    public void setHostContainer(Container hostContainer) {
        this.hostContainer = hostContainer;
    }

    protected void executeOperation(String scriptPath, Map<String, String> inputs) {
        try {
            hostContainer.exec(scriptPath, inputs);
        } catch (Exception e) {
            log.error("Failed executing " + scriptPath, e);
            throw new RuntimeException("Failed executing " + scriptPath, e);
        }
    }
}
