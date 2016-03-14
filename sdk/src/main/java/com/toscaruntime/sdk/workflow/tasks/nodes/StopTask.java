package com.toscaruntime.sdk.workflow.tasks.nodes;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.constants.InstanceState;
import tosca.nodes.Root;

public class StopTask extends AbstractTask {

    private static final Logger log = LoggerFactory.getLogger(StopTask.class);

    public StopTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
    }

    @Override
    public void doRun() {
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.STOPPING, false);
        try {
            nodeInstance.stop();
        } catch (Exception e) {
            log.warn(nodeInstance + " stop failed", e);
        }
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.CONFIGURED, true);
    }

    @Override
    public String toString() {
        return "Stop Task For " + nodeInstance.getId();
    }
}
