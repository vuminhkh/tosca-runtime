package com.toscaruntime.sdk.workflow.tasks;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.WorkflowExecution;

import tosca.nodes.Root;

/**
 * This task is used in case of scaling where we have connections to the outside or from the outside, we need to mock the lifecycle of the external source or target that they have already been created/started.
 *
 * @author Minh Khang VU
 */
public class MockTask extends AbstractTask {

    private String mockedTaskName;

    public MockTask(String mockedTaskName, Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
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

        MockTask mockTask = (MockTask) o;

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
        return "Mock " + this.mockedTaskName + " for " + nodeInstance.getId();
    }
}
