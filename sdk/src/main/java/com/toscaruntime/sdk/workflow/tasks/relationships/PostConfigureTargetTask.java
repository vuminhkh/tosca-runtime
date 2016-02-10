package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.constants.RelationshipInstanceState;
import tosca.nodes.Root;

public class PostConfigureTargetTask extends AbstractTask {

    public PostConfigureTargetTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceTargetRelationships = DeploymentUtil.getRelationshipInstanceByTargetId(relationshipInstances, nodeInstance.getId());
        nodeInstanceTargetRelationships.stream().filter(relationshipInstance ->
                // Only execute post configure once per instance
                relationshipInstance.getTarget().getPostConfiguredRelationshipNodes().add(relationshipInstance.getNode())
        ).forEach(relationshipInstance -> {
            relationshipInstance.postConfigureTarget();
            if (relationshipInstance.getState().equals(RelationshipInstanceState.POST_CONFIGURED_SOURCE)) {
                WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, RelationshipInstanceState.POST_CONFIGURED, true);
            } else {
                WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, RelationshipInstanceState.POST_CONFIGURED_TARGET, true);
            }
        });
    }

    @Override
    public String toString() {
        return "Post Configure Target task for " + nodeInstance.getId();
    }
}
