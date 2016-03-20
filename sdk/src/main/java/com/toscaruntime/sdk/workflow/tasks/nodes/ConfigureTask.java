package com.toscaruntime.sdk.workflow.tasks.nodes;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;

import tosca.constants.InstanceState;
import tosca.nodes.Root;

public class ConfigureTask extends AbstractNodeTask {

    public ConfigureTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
    }

    @Override
    public void doRun() {
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.CONFIGURING, false);
        nodeInstance.configure();
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.CONFIGURED, true);
    }

    @Override
    public String toString() {
        return "Configure Task For " + nodeInstance;
    }
}
