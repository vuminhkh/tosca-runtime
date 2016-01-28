package com.toscaruntime.sdk.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tosca.nodes.Root;

public class DeploymentDeleteInstancesModification {

    /**
     * Instances that will be deleted from the deployment
     */
    private Map<String, Root> instancesToDelete = new HashMap<>();


    /**
     * Relationship instances that will be deleted from the deployment
     */
    private Set<tosca.relationships.Root> relationshipInstancesToDelete = new HashSet<>();

    public DeploymentDeleteInstancesModification merge(DeploymentDeleteInstancesModification other) {
        getInstancesToDelete().putAll(other.instancesToDelete);
        getRelationshipInstancesToDelete().addAll(other.relationshipInstancesToDelete);
        return this;
    }

    public Map<String, Root> getInstancesToDelete() {
        return instancesToDelete;
    }

    public Set<tosca.relationships.Root> getRelationshipInstancesToDelete() {
        return relationshipInstancesToDelete;
    }

}
