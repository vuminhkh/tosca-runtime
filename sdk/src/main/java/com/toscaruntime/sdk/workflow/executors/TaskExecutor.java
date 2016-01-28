package com.toscaruntime.sdk.workflow.executors;

public class TaskExecutor implements Executor<Task> {

    @Override
    public void execute(Task task) {
        task.run();
    }
}
