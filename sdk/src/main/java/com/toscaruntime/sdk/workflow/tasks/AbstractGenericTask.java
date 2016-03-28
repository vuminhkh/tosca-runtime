package com.toscaruntime.sdk.workflow.tasks;

/**
 * Base class for generic, non operation task
 *
 * @author Minh Khang VU
 */
public abstract class AbstractGenericTask extends AbstractTask {

    protected String taskId;

    public AbstractGenericTask(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }
}
