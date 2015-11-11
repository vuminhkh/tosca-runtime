package com.toscaruntime.sdk.workflow;

public class SequenceExecutor implements Executor<Sequence> {

    public void execute(Sequence sequence) {
        for (final Action action : sequence.getActionList()) {
            TaskExecutorFactory.getExecutor(action).execute(action);
        }
    }
}
