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

public class SimpleOutputHandler implements OutputHandler {

    private static final Logger log = LoggerFactory.getLogger(SimpleOutputHandler.class);

    private ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {

        private AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Docker_Log_Output_Thread_" + count.incrementAndGet());
            return t;
        }
    });

    private Future<?> outFuture;

    private Future<?> errFuture;

    private void readStream(String source, InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info(source + " : " + line);
            }
        }
    }

    @Override
    public void handleStdOut(InputStream stdOut) {
        outFuture = executorService.submit(() -> {
            try {
                readStream("stdOut", stdOut);
            } catch (IOException e) {
                log.warn("Unable to read stdout", e);
            }
        });
    }

    @Override
    public void handleStdErr(InputStream stdErr) {
        errFuture = executorService.submit(() -> {
            try {
                readStream("stdErr", stdErr);
            } catch (IOException e) {
                log.warn("Unable to read stderr", e);
            }
        });
    }

    @Override
    public OperationOutput getOperationOutput() throws ExecutionException, InterruptedException {
        outFuture.get();
        errFuture.get();
        return new OperationOutput(null, new HashMap<>());
    }

    @Override
    public OperationOutput tryGetOperationOutput() {
        if (outFuture.isDone() && errFuture.isDone()) {
            return new OperationOutput(null, new HashMap<>());
        } else {
            return null;
        }
    }

    @Override
    public void close() {
        executorService.shutdown();
    }
}
