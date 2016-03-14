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

    public PreConfigureSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceSourceRelationships = DeploymentUtil.getRelationshipInstanceBySourceId(relationshipInstances, nodeInstance.getId());
        nodeInstanceSourceRelationships.stream().filter(relationshipInstance ->
                relationshipInstance.getSource().getPreConfiguredRelationshipNodes().add(relationshipInstance.getNode())
        ).forEach(relationshipInstance -> {
            relationshipInstance.preConfigureSource();
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.PRE_CONFIGURING, RelationshipInstanceState.PRE_CONFIGURED);
        });
    }

    @Override
    public String toString() {
        return "Pre Configure Source task for " + nodeInstance.getId();
    }
}
