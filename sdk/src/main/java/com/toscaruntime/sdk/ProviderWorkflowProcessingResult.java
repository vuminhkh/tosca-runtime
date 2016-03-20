package com.toscaruntime.sdk;

import java.util.Map;
import java.util.Set;

import tosca.nodes.Root;

public class ProviderWorkflowProcessingResult {

    private Map<String, Root> nodeInstances;

    private Set<tosca.relationships.Root> relationshipInstances;

    public ProviderWorkflowProcessingResult(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        this.nodeInstances = nodeInstances;
        this.relationshipInstances = relationshipInstances;
    }

    public Map<String, Root> getNodeInstances() {
        return nodeInstances;
    }

    public Set<tosca.relationships.Root> getRelationshipInstances() {
        return relationshipInstances;
    }
}
