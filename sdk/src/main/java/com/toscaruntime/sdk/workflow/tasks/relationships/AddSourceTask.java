package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;

import tosca.constants.RelationshipInstanceState;
import tosca.nodes.Root;

public class AddSourceTask extends AbstractRelationshipTask {

    public AddSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
    }

    @Override
    protected void doRun() {
        synchronized (relationshipInstance.getTarget()) {
            // Do not add source on the same target instance in concurrence
            relationshipInstance.addSource();
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.ESTABLISHING, RelationshipInstanceState.ESTABLISHED);
        }
    }

    @Override
    public String toString() {
        return "Add Source task for relationship " + relationshipInstance;
    }
}
