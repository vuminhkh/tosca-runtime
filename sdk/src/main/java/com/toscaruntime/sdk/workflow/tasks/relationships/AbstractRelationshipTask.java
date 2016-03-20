package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.relationships.Root;

public abstract class AbstractRelationshipTask extends AbstractTask {

    protected Root relationshipInstance;

    public AbstractRelationshipTask(Map<String, tosca.nodes.Root> nodeInstances, Set<Root> relationshipInstances, Root relationshipInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, workflowExecution);
        this.relationshipInstance = relationshipInstance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractRelationshipTask that = (AbstractRelationshipTask) o;

        return relationshipInstance.equals(that.relationshipInstance);

    }

    @Override
    public int hashCode() {
        return relationshipInstance.hashCode();
    }
}
