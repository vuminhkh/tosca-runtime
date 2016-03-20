package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;

import tosca.constants.RelationshipInstanceState;
import tosca.nodes.Root;

public class PreConfigureTargetTask extends AbstractRelationshipTask {

    public PreConfigureTargetTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
    }

    @Override
    protected void doRun() {
        if (relationshipInstance.getTarget().getPreConfiguredRelationshipNodes().add(relationshipInstance.getNode())) {
            relationshipInstance.preConfigureTarget();
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.PRE_CONFIGURING, RelationshipInstanceState.PRE_CONFIGURED);
        }
    }

    @Override
    public String toString() {
        return "Pre Configure Target task for " + relationshipInstance;
    }
}
