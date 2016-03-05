package com.toscaruntime.sdk.workflow;

import java.util.List;

public interface WorkflowExecutionListener {

    void onFinish();

    void onFailure(List<Throwable> e);
}
