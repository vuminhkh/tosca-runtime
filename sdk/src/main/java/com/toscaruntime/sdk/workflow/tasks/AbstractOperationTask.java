package com.toscaruntime.sdk.workflow.tasks;

import java.util.Map;
import java.util.Set;

import tosca.nodes.Root;

/**
 * Base class for node and relationship operation execution tasks
 *
 * @author Minh Khang VU
 */
public abstract class AbstractOperationTask extends AbstractTask {

    protected Map<String, Root> nodeInstances;

    protected Set<tosca.relationships.Root> relationshipInstances;

    public AbstractOperationTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        this.nodeInstances = nodeInstances;
        this.relationshipInstances = relationshipInstances;
    }

    public abstract String getInterfaceName();

    public abstract String getOperationName();

    public Map<String, Root> getNodeInstances() {
        return nodeInstances;
    }

    public Set<tosca.relationships.Root> getRelationshipInstances() {
        return relationshipInstances;
    }
}
