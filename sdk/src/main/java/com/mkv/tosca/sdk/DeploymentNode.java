package com.mkv.tosca.sdk;

import java.util.List;
import java.util.Map;

import tosca.nodes.Root;

public class DeploymentNode {

    private String id;

    private Map<String, Object> properties;

    private List<Root> instances;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public List<Root> getInstances() {
        return instances;
    }

    public void setInstances(List<Root> instances) {
        this.instances = instances;
    }
}
