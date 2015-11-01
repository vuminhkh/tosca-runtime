package com.mkv.tosca.sdk.workflow;

import com.mkv.tosca.exception.NonRecoverableException;

public class TaskExecutorFactory {

    private static final ParallelExecutor PARALLEL_EXECUTOR = new ParallelExecutor();

    private static final SequenceExecutor SEQUENCE_EXECUTOR = new SequenceExecutor();

    private static final TaskExecutor TASK_EXECUTOR = new TaskExecutor();

    public static ParallelExecutor getParallelExecutor() {
        return PARALLEL_EXECUTOR;
    }

    public static SequenceExecutor getSequenceExecutor() {
        return SEQUENCE_EXECUTOR;
    }

    public static TaskExecutor getTaskExecutor() {
        return TASK_EXECUTOR;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Action> Executor<T> getExecutor(T action) {
        if (action instanceof Parallel) {
            return (Executor<T>) PARALLEL_EXECUTOR;
        } else if (action instanceof Sequence) {
            return (Executor<T>) SEQUENCE_EXECUTOR;
        } else if (action instanceof Task) {
            return (Executor<T>) TASK_EXECUTOR;
        } else {
            throw new NonRecoverableException("Action of type [" + action.getClass() + "] is not supported");
        }
    }
}
