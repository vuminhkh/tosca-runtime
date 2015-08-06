package com.mkv.tosca.sdk.workflow;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.collect.Lists;
import com.mkv.exception.NonRecoverableException;

public class ParallelExecutor implements Executor<Parallel> {

    private ExecutorService executorService;

    public ParallelExecutor() {
        super();
        executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void execute(Parallel parallel) {
        List<Future<?>> futures = Lists.newArrayList();
        for (final Action action : parallel.getActionList()) {
            futures.add(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    TaskExecutorFactory.getExecutor(action).execute(action);
                }
            }));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new NonRecoverableException("Encounter error while executing task in parallel", e);
            }
        }
    }
}
