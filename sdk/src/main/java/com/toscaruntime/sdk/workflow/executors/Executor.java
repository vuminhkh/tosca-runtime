package com.toscaruntime.sdk.workflow.executors;

public interface Executor<T extends Action> {

    void execute(T action);
}
