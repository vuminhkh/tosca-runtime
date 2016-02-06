package com.toscaruntime.sdk.workflow.tasks.relationships;

import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

/**
 * This task is used in case of scaling where we have connections to the outside or from the outside, we need to mock the lifecycle of the external source or target that they have already been created/started.
 *
 * @author Minh Khang VU
 */
public class MockTask extends AbstractTask {

    public MockTask(AbstractTask mockedFrom) {
        super(mockedFrom.getNodeInstances(), mockedFrom.getRelationshipInstances(), mockedFrom.getNodeInstance(), mockedFrom.getTaskExecutor(), mockedFrom.getWorkflowExecution());
    }

    @Override
    protected void doRun() {
        // Mock task do not run
    }
}
