package com.toscaruntime.sdk.workflow.tasks.nodes;

import com.toscaruntime.sdk.util.WorkflowUtil;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

/**
 * Generic tasks to be executed on a node
 *
 * @author Minh Khang VU
 */
public class GenericNodeTask extends AbstractNodeTask {

    private String interfaceName;

    private String operationName;

    public GenericNodeTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, String interfaceName, String operationName) {
        super(nodeInstances, relationshipInstances, nodeInstance);
        this.interfaceName = interfaceName;
        this.operationName = operationName;
    }

    @Override
    public String getInterfaceName() {
        return interfaceName;
    }

    @Override
    public String getOperationName() {
        return operationName;
    }

    @Override
    protected void doRun() throws Throwable {
        WorkflowUtil.invokeRuntimeTypeMethod(nodeInstance, nodeInstances, relationshipInstances, interfaceName, operationName);
    }
}
