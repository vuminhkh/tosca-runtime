package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;

import tosca.constants.RelationshipInstanceState;
import tosca.nodes.Root;

public class AddTargetTask extends AbstractRelationshipTask {

    public AddTargetTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
    }

    @Override
    protected void doRun() {
        synchronized (relationshipInstance.getSource()) {
            // Do not add target on the same source instance in concurrence
            relationshipInstance.addTarget();
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.ESTABLISHING, RelationshipInstanceState.ESTABLISHED);
        }
    }

    @Override
    public String toString() {
        return "Add Target task for " + relationshipInstance;
    }
}

