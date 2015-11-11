package com.toscaruntime.sdk.workflow;

public class TaskExecutor implements Executor<Task> {

    @Override
    public void execute(Task task) {
        task.run();
    }
}
