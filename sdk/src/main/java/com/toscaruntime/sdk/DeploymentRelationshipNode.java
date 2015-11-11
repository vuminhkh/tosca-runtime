package com.toscaruntime.sdk;

import java.util.List;
import java.util.Map;

import tosca.relationships.Root;

public class DeploymentRelationshipNode {

    private String sourceNodeId;

    private String targetNodeId;

    private Map<String, Object> properties;

    private List<Root> relationshipInstances;

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public List<Root> getRelationshipInstances() {
        return relationshipInstances;
    }

    public void setRelationshipInstances(List<Root> relationshipInstances) {
        this.relationshipInstances = relationshipInstances;
    }
}
