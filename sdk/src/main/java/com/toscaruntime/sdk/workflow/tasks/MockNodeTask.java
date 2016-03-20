package com.toscaruntime.sdk.workflow.tasks;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.nodes.AbstractNodeTask;

import tosca.nodes.Root;

public class MockNodeTask extends AbstractNodeTask {

    private String mockedTaskName;

    public MockNodeTask(String mockedTaskName, Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
        super(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        this.mockedTaskName = mockedTaskName;
    }

    @Override
    protected void doRun() {
        // Mock task do not run
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        MockNodeTask mockTask = (MockNodeTask) o;

        return mockedTaskName != null ? mockedTaskName.equals(mockTask.mockedTaskName) : mockTask.mockedTaskName == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (mockedTaskName != null ? mockedTaskName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Mock " + this.mockedTaskName + " for " + nodeInstance;
    }
}
