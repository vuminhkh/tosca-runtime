package com.toscaruntime.sdk.workflow.tasks.nodes;

import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.sdk.util.WorkflowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tosca.constants.InstanceState;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class StopTask extends AbstractNodeTask {

    private static final Logger log = LoggerFactory.getLogger(StopTask.class);

    public StopTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
        super(nodeInstances, relationshipInstances, nodeInstance);
    }

    @Override
    public void doRunNodeOperation() {
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.STOPPING, false);
        try {
            nodeInstance.stop();
        } catch (Exception e) {
            log.warn(nodeInstance + " stop failed", e);
        }
        WorkflowUtil.refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, InstanceState.CONFIGURED, true);
    }

    @Override
    public String getInterfaceName() {
        return ToscaInterfaceConstant.NODE_STANDARD_INTERFACE;
    }

    @Override
    public String getOperationName() {
        return ToscaInterfaceConstant.STOP_OPERATION;
    }

    @Override
    public String toString() {
        return "Stop Task For " + nodeInstance;
    }
}
