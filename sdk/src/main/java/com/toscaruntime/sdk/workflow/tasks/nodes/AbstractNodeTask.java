package com.toscaruntime.sdk.workflow.tasks.nodes;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

import tosca.nodes.Root;

public abstract class AbstractNodeTask extends AbstractTask {

    protected Root nodeInstance;

    public AbstractNodeTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, workflowExecution);
        this.nodeInstance = nodeInstance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractNodeTask that = (AbstractNodeTask) o;

        return nodeInstance.equals(that.nodeInstance);

    }

    @Override
    public int hashCode() {
        return nodeInstance.hashCode();
    }
}
