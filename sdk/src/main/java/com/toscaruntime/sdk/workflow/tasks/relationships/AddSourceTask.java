package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.sdk.util.WorkflowUtil;

import com.toscaruntime.constant.RelationshipInstanceState;
import tosca.nodes.Root;

public class AddSourceTask extends AbstractRelationshipTask {

    public AddSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance) {
        super(nodeInstances, relationshipInstances, relationshipInstance);
    }

    @Override
    protected void doRunRelationshipOperation() {
        synchronized (relationshipInstance.getTarget()) {
            // Do not add source on the same target instance in concurrence
            relationshipInstance.addSource();
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.ESTABLISHING, RelationshipInstanceState.ESTABLISHED);
        }
    }

    @Override
    public String getInterfaceName() {
        return ToscaInterfaceConstant.RELATIONSHIP_STANDARD_INTERFACE;
    }

    @Override
    public String getOperationName() {
        return ToscaInterfaceConstant.ADD_SOURCE_OPERATION;
    }

    @Override
    public String toString() {
        return "Add Source task for relationship " + relationshipInstance;
    }
}
