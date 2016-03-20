package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;

import tosca.constants.RelationshipInstanceState;
import tosca.nodes.Root;

public class PostConfigureSourceTask extends AbstractRelationshipTask {

    public PostConfigureSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
    }

    @Override
    protected void doRun() {
        if (relationshipInstance.getSource().getPostConfiguredRelationshipNodes().add(relationshipInstance.getNode())) {
            relationshipInstance.postConfigureSource();
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.POST_CONFIGURING, RelationshipInstanceState.POST_CONFIGURED);
        }
    }

    @Override
    public String toString() {
        return "Post Configure Source task for " + relationshipInstance;
    }
}
