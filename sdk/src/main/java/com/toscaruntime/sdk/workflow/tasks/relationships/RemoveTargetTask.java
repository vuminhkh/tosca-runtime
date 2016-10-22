package com.toscaruntime.sdk.workflow.tasks.relationships;

import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.sdk.util.WorkflowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.toscaruntime.constant.RelationshipInstanceState;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class RemoveTargetTask extends AbstractRelationshipTask {

    private static final Logger log = LoggerFactory.getLogger(RemoveTargetTask.class);

    public RemoveTargetTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance) {
        super(nodeInstances, relationshipInstances, relationshipInstance);
    }

    @Override
    protected void doRunRelationshipOperation() {
        synchronized (relationshipInstance.getSource()) {
            try {
                relationshipInstance.removeTarget();
            } catch (Exception e) {
                log.warn(relationshipInstance + " removeTarget failed", e);
            }
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.UNLINKING, RelationshipInstanceState.POST_CONFIGURED);
        }
    }

    @Override
    public String getInterfaceName() {
        return ToscaInterfaceConstant.RELATIONSHIP_STANDARD_INTERFACE;
    }

    @Override
    public String getOperationName() {
        return ToscaInterfaceConstant.REMOVE_TARGET_OPERATION;
    }

    @Override
    public String toString() {
        return "Remove Target task for " + relationshipInstance;
    }
}
