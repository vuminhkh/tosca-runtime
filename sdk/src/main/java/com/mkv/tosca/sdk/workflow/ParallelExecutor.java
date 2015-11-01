package com.mkv.tosca.sdk.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.mkv.tosca.exception.NonRecoverableException;

public class ParallelExecutor implements Executor<Parallel> {

    private ExecutorService executorService;

    public ParallelExecutor() {
        super();
        executorService = Executors.newCachedThreadPool(new ThreadFactory() {
            private AtomicInteger count = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("WorkflowThread" + count.incrementAndGet());
                return t;
            }
        });
    }

    @Override
    public void execute(Parallel parallel) {
        List<Future<?>> futures = new ArrayList<>();
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
