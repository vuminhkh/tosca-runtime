package com.toscaruntime.sdk.workflow.tasks.nodes;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.sdk.util.WorkflowUtil;

import com.toscaruntime.constant.InstanceState;
import tosca.nodes.Root;

public class ConfigureTask extends AbstractNodeTask {

    public ConfigureTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
        super(nodeInstances, relationshipInstances, nodeInstance);
    }

    @Override
    public void doRunNodeOperation() {
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.CONFIGURING, false);
        nodeInstance.configure();
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.CONFIGURED, true);
    }

    @Override
    public String getInterfaceName() {
        return ToscaInterfaceConstant.NODE_STANDARD_INTERFACE;
    }

    @Override
    public String getOperationName() {
        return ToscaInterfaceConstant.CONFIGURE_OPERATION;
    }

    @Override
    public String toString() {
        return "Configure Task For " + nodeInstance;
    }
}
