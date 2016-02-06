package com.toscaruntime.sdk.workflow.tasks.nodes;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.nodes.Root;

public class StartTask extends AbstractTask {

    public StartTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    public void doRun() {
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, "starting", false);
        nodeInstance.start();
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, "started", true);
    }

    @Override
    public String toString() {
        return "Start Task For " + nodeInstance.getId();
    }
}
