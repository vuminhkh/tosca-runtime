package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.sdk.util.WorkflowUtil;

import com.toscaruntime.constant.RelationshipInstanceState;
import tosca.nodes.Root;

public class PreConfigureTargetTask extends AbstractRelationshipTask {

    public PreConfigureTargetTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance) {
        super(nodeInstances, relationshipInstances, relationshipInstance);
    }

    @Override
    protected void doRunRelationshipOperation() {
        if (relationshipInstance.getTarget().getPreConfiguredRelationshipNodes().add(relationshipInstance.getNode())) {
            relationshipInstance.preConfigureTarget();
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.PRE_CONFIGURING, RelationshipInstanceState.PRE_CONFIGURED);
        }
    }

    @Override
    public String getInterfaceName() {
        return ToscaInterfaceConstant.RELATIONSHIP_STANDARD_INTERFACE;
    }

    @Override
    public String getOperationName() {
        return ToscaInterfaceConstant.PRE_CONFIGURE_TARGET_OPERATION;
    }

    @Override
    public String toString() {
        return "Pre Configure Target task for " + relationshipInstance;
    }
}
