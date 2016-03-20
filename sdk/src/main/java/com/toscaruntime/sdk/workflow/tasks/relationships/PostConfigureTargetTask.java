package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;

import tosca.constants.RelationshipInstanceState;
import tosca.nodes.Root;

public class PostConfigureTargetTask extends AbstractRelationshipTask {

    public PostConfigureTargetTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
    }

    @Override
    protected void doRun() {
        if (relationshipInstance.getTarget().getPostConfiguredRelationshipNodes().add(relationshipInstance.getNode())) {
            relationshipInstance.postConfigureTarget();
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.POST_CONFIGURING, RelationshipInstanceState.POST_CONFIGURED);
        }
    }

    @Override
    public String toString() {
        return "Post Configure Target task for " + relationshipInstance;
    }
}
