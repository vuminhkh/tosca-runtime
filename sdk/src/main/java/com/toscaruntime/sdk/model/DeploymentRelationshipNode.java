package com.toscaruntime.sdk.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tosca.relationships.Root;

public class DeploymentRelationshipNode {

    private String sourceNodeId;

    private String targetNodeId;

    private Class<? extends tosca.relationships.Root> relationshipType;

    private Map<String, Object> properties;

    private List<Root> relationshipInstances = new ArrayList<>();

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

    public Class<? extends tosca.relationships.Root> getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(Class<? extends tosca.relationships.Root> relationshipType) {
        this.relationshipType = relationshipType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DeploymentRelationshipNode that = (DeploymentRelationshipNode) o;

        if (sourceNodeId != null ? !sourceNodeId.equals(that.sourceNodeId) : that.sourceNodeId != null) return false;
        if (targetNodeId != null ? !targetNodeId.equals(that.targetNodeId) : that.targetNodeId != null) return false;
        return relationshipType != null ? relationshipType.equals(that.relationshipType) : that.relationshipType == null;
    }

    @Override
    public int hashCode() {
        int result = sourceNodeId != null ? sourceNodeId.hashCode() : 0;
        result = 31 * result + (targetNodeId != null ? targetNodeId.hashCode() : 0);
        result = 31 * result + (relationshipType != null ? relationshipType.hashCode() : 0);
        return result;
    }
}
