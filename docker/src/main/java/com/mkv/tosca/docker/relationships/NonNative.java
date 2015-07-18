package com.mkv.tosca.docker.relationships;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tosca.relationships.Root;

import com.mkv.tosca.docker.nodes.Container;

/**
 * This is the base class for all non native relationships. It describes how operations should be executed.
 * Non native relationships are those which have an user defined implementation
 * 
 * @author Minh Khang VU
 */
public abstract class NonNative extends Root {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    private Container sourceHostContainer;

    private Container targetHostContainer;

    protected void executeSourceOperation(String scriptPath, Map<String, String> inputs) {
        try {
            sourceHostContainer.exec(scriptPath, inputs);
        } catch (Exception e) {
            log.error("Failed executing " + scriptPath + " on source " + sourceHostContainer.getName(), e);
            throw new RuntimeException("Failed executing " + scriptPath + " on source " + sourceHostContainer.getName(), e);
        }
    }

    protected void executeTargetOperation(String scriptPath, Map<String, String> inputs) {
        try {
            targetHostContainer.exec(scriptPath, inputs);
        } catch (Exception e) {
            log.error("Failed executing " + scriptPath + " on target " + sourceHostContainer.getName(), e);
            throw new RuntimeException("Failed executing " + scriptPath + " on target " + sourceHostContainer.getName(), e);
        }
    }

    public void setSourceHostContainer(Container sourceHostContainer) {
        this.sourceHostContainer = sourceHostContainer;
    }

    public void setTargetHostContainer(Container targetHostContainer) {
        this.targetHostContainer = targetHostContainer;
    }
}
