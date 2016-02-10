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

public class RemoveSourceTask extends AbstractTask {

    private static final Logger log = LoggerFactory.getLogger(RemoveSourceTask.class);

    public RemoveSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    protected void doRun() {
        Set<tosca.relationships.Root> nodeInstanceSourceRelationships = DeploymentUtil.getRelationshipInstanceBySourceId(relationshipInstances, nodeInstance.getId());
        nodeInstanceSourceRelationships.forEach(relationshipInstance -> {
            try {
                synchronized (relationshipInstance.getTarget()) {
                    WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, "removingSource", false);
                    relationshipInstance.removeSource();
                    WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, "removedSource", true);
                }
            } catch (Exception e) {
                log.warn(relationshipInstance + " removedSource failed", e);
            }
        });
    }

    @Override
    public String toString() {
        return "Remove Source task for " + nodeInstance.getId();
    }
}
