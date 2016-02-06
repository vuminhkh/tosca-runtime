package com.toscaruntime.sdk.workflow.tasks.nodes;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.nodes.Root;

public class DeleteTask extends AbstractTask {

    private static final Logger log = LoggerFactory.getLogger(DeleteTask.class);

    public DeleteTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    public void doRun() {
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, "deleting", false);
        try {
            nodeInstance.delete();
        } catch (Exception e) {
            log.warn(nodeInstance + " delete failed", e);
        }
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, "deleted", true);
    }

    @Override
    public String toString() {
        return "Delete Task For " + nodeInstance.getId();
    }
}
