package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.nodes.Root;

public class RemoveTargetTask extends AbstractTask {

    private static final Logger log = LoggerFactory.getLogger(RemoveTargetTask.class);

    public RemoveTargetTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceTargetRelationships = DeploymentUtil.getRelationshipInstanceByTargetId(relationshipInstances, nodeInstance.getId());
        nodeInstanceTargetRelationships.stream().forEach(relationshipInstance -> {
            try {
                synchronized (relationshipInstance.getSource()) {
                    WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, "removingTarget", false);
                    relationshipInstance.removeTarget();
                    WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, "removedTarget", true);
                }
            } catch (Exception e) {
                log.warn(relationshipInstance + " removeTarget failed", e);
            }
        });
    }
}
