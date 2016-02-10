package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.nodes.Root;

public class AddTargetTask extends AbstractTask {

    public AddTargetTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceSourceRelationships = DeploymentUtil.getRelationshipInstanceBySourceId(relationshipInstances, nodeInstance.getId());
        nodeInstanceSourceRelationships.stream().forEach(relationshipInstance -> {
            synchronized (relationshipInstance.getSource()) {
                WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, "addingTarget", false);
                // Do not add target on the same source instance in concurrence
                relationshipInstance.addTarget();
                WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, "addedTarget", true);
            }
        });
    }

    @Override
    public String toString() {
        return "Add Target task for " + nodeInstance.getId();
    }
}

