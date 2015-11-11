package com.toscaruntime.sdk.workflow;

public interface Executor<T extends Action> {

    void execute(T action);
}
