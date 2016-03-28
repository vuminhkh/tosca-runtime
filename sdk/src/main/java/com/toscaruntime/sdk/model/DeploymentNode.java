package com.toscaruntime.sdk.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.Deployment;

import tosca.nodes.Root;

public class DeploymentNode {

    private String id;

    private String parent;

    private String host;

    private Deployment deployment;

    private Class<? extends Root> type;

    private Set<String> children = new HashSet<>();

    private Map<String, Object> properties = new HashMap<>();

    private Map<String, Map<String, Object>> capabilitiesProperties = new HashMap<>();

    private Set<Root> instances = new HashSet<>();

    private int instancesCount;

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

    public Set<Root> getInstances() {
        return instances;
    }

    public void setInstances(Set<Root> instances) {
        this.instances = instances;
    }

    public Map<String, Map<String, Object>> getCapabilitiesProperties() {
        return capabilitiesProperties;
    }

    public void setCapabilitiesProperties(Map<String, Map<String, Object>> capabilitiesProperties) {
        this.capabilitiesProperties = capabilitiesProperties;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getMaxInstancesCount() {
        return getScalingPolicy("max_instances");
    }

    public int getMinInstancesCount() {
        return getScalingPolicy("min_instances");
    }

    public int getDefaultInstancesCount() {
        return getScalingPolicy("default_instances");
    }

    private int getScalingPolicy(String propertyName) {
        Map<String, Object> scalableProperties = capabilitiesProperties.get("scalable");
        if (scalableProperties == null) {
            return 1;
        } else {
            Object defaultInstances = scalableProperties.get(propertyName);
            if (defaultInstances == null) {
                return 1;
            } else {
                return Integer.parseInt(scalableProperties.get(propertyName).toString());
            }
        }
    }

    public void initialLoad() {
        this.instancesCount = deployment.getDeploymentPersister().syncGetNodeInstancesCount(getId());
    }

    public int getInstancesCount() {
        return instancesCount;
    }

    public void setInstancesCount(int instancesCount) {
        this.instancesCount = instancesCount;
    }

    public Class<? extends Root> getType() {
        return type;
    }

    public void setType(Class<? extends Root> type) {
        this.type = type;
    }

    public Set<String> getChildren() {
        return children;
    }

    public void setChildren(Set<String> children) {
        this.children = children;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }
}
