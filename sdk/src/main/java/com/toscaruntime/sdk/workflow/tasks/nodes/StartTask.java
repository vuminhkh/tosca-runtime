package com.toscaruntime.sdk.workflow.tasks.nodes;

import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.sdk.util.WorkflowUtil;
import tosca.constants.InstanceState;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class StartTask extends AbstractNodeTask {

    public StartTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
        super(nodeInstances, relationshipInstances, nodeInstance);
    }

    @Override
    public void doRunNodeOperation() {
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.STARTING, false);
        nodeInstance.start();
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.STARTED, true);
    }

    @Override
    public String getInterfaceName() {
        return ToscaInterfaceConstant.NODE_STANDARD_INTERFACE;
    }

    @Override
    public String getOperationName() {
        return ToscaInterfaceConstant.START_OPERATION;
    }

    @Override
    public String toString() {
        return "Start Task For " + nodeInstance;
    }
}
