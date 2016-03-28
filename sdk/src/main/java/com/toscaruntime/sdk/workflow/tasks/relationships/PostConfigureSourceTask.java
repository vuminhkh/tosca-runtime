package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.sdk.util.WorkflowUtil;

import tosca.constants.RelationshipInstanceState;
import tosca.nodes.Root;

public class PostConfigureSourceTask extends AbstractRelationshipTask {

    public PostConfigureSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance) {
        super(nodeInstances, relationshipInstances, relationshipInstance);
    }

    @Override
    protected void doRun() {
        if (relationshipInstance.getSource().getPostConfiguredRelationshipNodes().add(relationshipInstance.getNode())) {
            relationshipInstance.postConfigureSource();
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.POST_CONFIGURING, RelationshipInstanceState.POST_CONFIGURED);
        }
    }

    @Override
    public String getInterfaceName() {
        return ToscaInterfaceConstant.RELATIONSHIP_STANDARD_INTERFACE;
    }

    @Override
    public String getOperationName() {
        return ToscaInterfaceConstant.POST_CONFIGURE_SOURCE_OPERATION;
    }

    @Override
    public String toString() {
        return "Post Configure Source task for " + relationshipInstance;
    }
}
