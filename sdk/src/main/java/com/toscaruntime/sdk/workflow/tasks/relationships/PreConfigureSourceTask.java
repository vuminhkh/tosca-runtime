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

public class PreConfigureSourceTask extends AbstractTask {

    public PreConfigureSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceSourceRelationships = DeploymentUtil.getRelationshipInstanceBySourceId(relationshipInstances, nodeInstance.getId());
        nodeInstanceSourceRelationships.stream().filter(relationshipInstance ->
                relationshipInstance.getSource().getPreConfiguredRelationshipNodes().add(relationshipInstance.getNode())
        ).forEach(relationshipInstance -> {
            relationshipInstance.preConfigureSource();
            if (relationshipInstance.getState().equals(RelationshipInstanceState.PRE_CONFIGURED_TARGET)) {
                WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, RelationshipInstanceState.PRE_CONFIGURED, true);
            } else {
                WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, RelationshipInstanceState.PRE_CONFIGURED_SOURCE, true);
            }
        });
    }

    @Override
    public String toString() {
        return "Pre Configure Source task for " + nodeInstance.getId();
    }
}
