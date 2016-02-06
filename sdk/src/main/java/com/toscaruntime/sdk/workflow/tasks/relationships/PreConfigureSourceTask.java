package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.nodes.Root;

public class PreConfigureSourceTask extends AbstractTask {

    public PreConfigureSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceSourceRelationships = DeploymentUtil.getRelationshipInstanceBySourceId(relationshipInstances, nodeInstance.getId());
        for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
            if (relationship.getSource().getPreConfiguredRelationshipNodes().add(relationship.getNode())) {
                WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "preConfiguringSource", false);
                relationship.preConfigureSource();
                WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "preConfiguredSource", true);
            }
        }
    }
}
