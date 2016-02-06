package com.toscaruntime.sdk.workflow.tasks.nodes;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.nodes.Root;

public class ConfigureTask extends AbstractTask {

    public ConfigureTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, taskExecutor, workflowExecution);
    }

    @Override
    public void doRun() {
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, "configuring", false);
        nodeInstance.configure();
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, "configured", true);
    }

    @Override
    public String toString() {
        return "Configure Task For " + nodeInstance.getId();
    }
}
