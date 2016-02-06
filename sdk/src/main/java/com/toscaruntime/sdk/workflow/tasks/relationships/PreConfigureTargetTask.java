package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.nodes.Root;

public class PreConfigureTargetTask extends AbstractTask {

    public PreConfigureTargetTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceTargetRelationships = DeploymentUtil.getRelationshipInstanceByTargetId(relationshipInstances, nodeInstance.getId());
        for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
            if (relationship.getTarget().getPreConfiguredRelationshipNodes().add(relationship.getNode())) {
                WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "preConfiguringTarget", false);
                relationship.preConfigureTarget();
                WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "preConfiguredTarget", true);
            }
        }
    }
}
