package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;

import tosca.constants.RelationshipInstanceState;
import tosca.nodes.Root;

public class RemoveSourceTask extends AbstractRelationshipTask {

    private static final Logger log = LoggerFactory.getLogger(RemoveSourceTask.class);

    public RemoveSourceTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
    }

    @Override
    protected void doRun() {
        synchronized (relationshipInstance.getTarget()) {
            try {
                relationshipInstance.removeSource();
            } catch (Exception e) {
                log.warn(relationshipInstance + " removeSource failed", e);
            }
            WorkflowUtil.changeRelationshipState(relationshipInstance, nodeInstances, relationshipInstances, RelationshipInstanceState.UNLINKING, RelationshipInstanceState.POST_CONFIGURED);
        }
    }

    @Override
    public String toString() {
        return "Remove Source task for " + relationshipInstance;
    }
}
