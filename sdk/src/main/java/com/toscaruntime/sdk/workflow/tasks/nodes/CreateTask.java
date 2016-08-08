package com.toscaruntime.sdk.workflow.tasks.nodes;

import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.sdk.util.WorkflowUtil;
import tosca.constants.InstanceState;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class CreateTask extends AbstractNodeTask {

    public CreateTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
        super(nodeInstances, relationshipInstances, nodeInstance);
    }

    @Override
    public void doRunNodeOperation() {
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.CREATING, false);
        nodeInstance.create();
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.CREATED, true);
    }

    @Override
    public String getInterfaceName() {
        return ToscaInterfaceConstant.NODE_STANDARD_INTERFACE;
    }

    @Override
    public String getOperationName() {
        return ToscaInterfaceConstant.CREATE_OPERATION;
    }

    @Override
    public String toString() {
        return "Create Task For " + nodeInstance;
    }
}
