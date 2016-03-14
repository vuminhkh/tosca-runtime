package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.constants.RelationshipInstanceState;
import tosca.nodes.Root;

public class RemoveTargetTask extends AbstractTask {

    private static final Logger log = LoggerFactory.getLogger(RemoveTargetTask.class);

    public RemoveTargetTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceTargetRelationships = DeploymentUtil.getRelationshipInstanceByTargetId(relationshipInstances, nodeInstance.getId());
        nodeInstanceTargetRelationships.stream().forEach(relationshipInstance -> {
            try {
                synchronized (relationshipInstance.getSource()) {
                    relationshipInstance.removeTarget();
                    WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.UNLINKING, RelationshipInstanceState.POST_CONFIGURED);
                }
            } catch (Exception e) {
                log.warn(relationshipInstance + " removeTarget failed", e);
            }
        });
    }

    @Override
    public String toString() {
        return "Remove Target task for " + nodeInstance.getId();
    }
}
