package com.toscaruntime.sdk.workflow;

import java.util.Collection;

public interface WorkflowExecutionListener {

    void onStop();

    void onCancel();

    void onFinish();

    void onFailure(Collection<Throwable> e);
}
