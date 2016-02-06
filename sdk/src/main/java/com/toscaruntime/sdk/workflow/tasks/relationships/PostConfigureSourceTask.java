package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.nodes.Root;

public class PostConfigureSourceTask extends AbstractTask {

    public PostConfigureSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceSourceRelationships = DeploymentUtil.getRelationshipInstanceBySourceId(relationshipInstances, nodeInstance.getId());
        for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
            WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "postConfiguringSource", false);
            if (relationship.getSource().getPostConfiguredRelationshipNodes().add(relationship.getNode())) {
                relationship.postConfigureSource();
            }
            WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "postConfiguredSource", true);
        }
    }
}
