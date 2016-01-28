package com.toscaruntime.sdk.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tosca.nodes.Root;

public class DeploymentAddInstancesModification {

    /**
     * Instances that will be added to the deployment
     */
    private Map<String, Root> instancesToAdd = new HashMap<>();

    /**
     * Relationship instances that will be added to the deployment
     */
    private Set<tosca.relationships.Root> relationshipInstancesToAdd = new HashSet<>();

    /**
     * All relationship nodes that will be impacted by the creation of new instances
     */
    private Set<DeploymentRelationshipNode> impactedRelationshipNodesByAddition = new HashSet<>();

    /**
     * All nodes that will be impacted by the creation of new instances
     */
    private Set<DeploymentNode> impactedNodesByAddition = new HashSet<>();

    /**
     * Instances that will be impacted by the creation of new instances (which have relationship with newly created instances).
     * Note that those instances have already been created in the current deployment.
     */
    private Map<String, Root> impactedInstancesByAddition = new HashMap<>();

    public DeploymentAddInstancesModification merge(DeploymentAddInstancesModification other) {
        getInstancesToAdd().putAll(other.instancesToAdd);
        getRelationshipInstancesToAdd().addAll(other.relationshipInstancesToAdd);
        getImpactedRelationshipNodesByAddition().addAll(other.impactedRelationshipNodesByAddition);
        getImpactedInstancesByAddition().putAll(other.impactedInstancesByAddition);
        getImpactedNodesByAddition().addAll(other.impactedNodesByAddition);
        return this;
    }

    public Map<String, Root> getInstancesToAdd() {
        return instancesToAdd;
    }

    public Set<tosca.relationships.Root> getRelationshipInstancesToAdd() {
        return relationshipInstancesToAdd;
    }

    public Set<DeploymentRelationshipNode> getImpactedRelationshipNodesByAddition() {
        return impactedRelationshipNodesByAddition;
    }

    public Map<String, Root> getImpactedInstancesByAddition() {
        return impactedInstancesByAddition;
    }

    public Set<DeploymentNode> getImpactedNodesByAddition() {
        return impactedNodesByAddition;
    }

}
