package com.toscaruntime.sdk.workflow.tasks.relationships;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.tasks.AbstractOperationTask;

import tosca.relationships.Root;

public abstract class AbstractRelationshipTask extends AbstractOperationTask {

    protected Root relationshipInstance;

    public AbstractRelationshipTask(Map<String, tosca.nodes.Root> nodeInstances, Set<Root> relationshipInstances, Root relationshipInstance) {
        super(nodeInstances, relationshipInstances);
        this.relationshipInstance = relationshipInstance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractRelationshipTask that = (AbstractRelationshipTask) o;

        return relationshipInstance.equals(that.relationshipInstance);

    }

    public Root getRelationshipInstance() {
        return relationshipInstance;
    }

    @Override
    public int hashCode() {
        return relationshipInstance.hashCode();
    }
}
