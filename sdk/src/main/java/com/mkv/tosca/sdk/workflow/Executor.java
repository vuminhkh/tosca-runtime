package com.mkv.tosca.sdk.workflow;

public interface Executor<T extends Action> {

    void execute(T action);
}
