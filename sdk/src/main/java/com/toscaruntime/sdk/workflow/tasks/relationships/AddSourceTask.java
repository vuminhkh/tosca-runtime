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

public class AddSourceTask extends AbstractTask {

    public AddSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceTargetRelationships = DeploymentUtil.getRelationshipInstanceByTargetId(relationshipInstances, nodeInstance.getId());
        nodeInstanceTargetRelationships.stream().forEach(relationshipInstance -> {
            synchronized (relationshipInstance.getTarget()) {
                // Do not add source on the same target instance in concurrence
                relationshipInstance.addSource();
                if (relationshipInstance.getState().equals(RelationshipInstanceState.ADDED_TARGET)) {
                    WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, RelationshipInstanceState.ESTABLISHED, true);
                } else {
                    WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, RelationshipInstanceState.ADDED_SOURCE, true);
                }
            }
        });
    }

    @Override
    public String toString() {
        return "Add Source task for " + nodeInstance.getId();
    }
}
