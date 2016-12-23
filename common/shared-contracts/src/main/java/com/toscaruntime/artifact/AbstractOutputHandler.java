package com.toscaruntime.artifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractOutputHandler implements OutputHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractOutputHandler.class);

    private ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {

        private AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("CommandOutputThread" + count.incrementAndGet());
            return t;
        }
    });

    private Future<?> outFuture;

    private Future<?> errFuture;

    private void readStream(String source, InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                onData(source, line);
            }
        }
    }

    @Override
    public void handleStdOut(InputStream stdOut) {
        outFuture = executorService.submit(() -> {
            try {
                readStream("stdout", stdOut);
            } catch (IOException e) {
                log.warn("Unable to read stdout", e);
            }
        });
    }

    @Override
    public void handleStdErr(InputStream stdErr) {
        errFuture = executorService.submit(() -> {
            try {
                readStream("stderr", stdErr);
            } catch (IOException e) {
                log.warn("Unable to read stderr", e);
            }
        });
    }

    @Override
    public OperationOutput getOperationOutput() throws ExecutionException, InterruptedException {
        waitForOutputToBeConsumed();
        return doGetOutput();
    }

    protected boolean isDone() {
        return outFuture.isDone() && errFuture.isDone();
    }

    protected OperationOutput doGetOutput() {
        return new OperationOutput(null, new HashMap<>());
    }

    @Override
    public OperationOutput tryGetOperationOutput() {
        if (isDone()) {
            return doGetOutput();
        } else {
            return null;
        }
    }

    @Override
    public void waitForOutputToBeConsumed() throws ExecutionException, InterruptedException {
        outFuture.get();
        errFuture.get();
    }

    @Override
    public void close() {
        executorService.shutdown();
    }

    protected abstract void onData(String source, String line);
}
