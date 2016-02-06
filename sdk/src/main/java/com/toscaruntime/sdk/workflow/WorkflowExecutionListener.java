package com.toscaruntime.sdk.workflow;

public interface WorkflowExecutionListener {

    void onFinish();

    void onFailure(Throwable e);
}
