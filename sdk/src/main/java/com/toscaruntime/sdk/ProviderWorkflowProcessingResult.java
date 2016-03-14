package com.toscaruntime.sdk;

import java.util.Map;
import java.util.Set;

import tosca.nodes.BlockStorage;
import tosca.nodes.Compute;
import tosca.nodes.Network;
import tosca.nodes.Root;

public class ProviderWorkflowProcessingResult {

    private Map<String, Root> nodeInstances;

    private Set<tosca.relationships.Root> relationshipInstances;

    private Set<Compute> computes;

    private Set<BlockStorage> blockStorages;

    private Set<Network> networks;

    public ProviderWorkflowProcessingResult(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Set<Compute> computes, Set<BlockStorage> blockStorages, Set<Network> networks) {
        this.nodeInstances = nodeInstances;
        this.relationshipInstances = relationshipInstances;
        this.computes = computes;
        this.blockStorages = blockStorages;
        this.networks = networks;
    }

    public Map<String, Root> getNodeInstances() {
        return nodeInstances;
    }

    public Set<tosca.relationships.Root> getRelationshipInstances() {
        return relationshipInstances;
    }

    public Set<Compute> getComputes() {
        return computes;
    }

    public Set<BlockStorage> getBlockStorages() {
        return blockStorages;
    }

    public Set<Network> getNetworks() {
        return networks;
    }
}
