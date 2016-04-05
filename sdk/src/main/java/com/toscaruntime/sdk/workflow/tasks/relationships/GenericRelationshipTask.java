package com.toscaruntime.sdk.workflow.tasks.relationships;

import com.toscaruntime.sdk.util.WorkflowUtil;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class GenericRelationshipTask extends AbstractRelationshipTask {

    private String interfaceName;

    private String operationName;

    public GenericRelationshipTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, String interfaceName, String operationName) {
        super(nodeInstances, relationshipInstances, relationshipInstance);
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
        WorkflowUtil.invokeRuntimeTypeMethod(relationshipInstance, nodeInstances, relationshipInstances, interfaceName, operationName);
    }
}
