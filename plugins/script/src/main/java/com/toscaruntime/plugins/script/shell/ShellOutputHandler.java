package com.toscaruntime.plugins.script.shell;

import com.toscaruntime.artifact.OperationOutput;
import com.toscaruntime.artifact.OutputHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ShellOutputHandler implements OutputHandler {

    private static final Logger log = LoggerFactory.getLogger(ShellOutputHandler.class);

    private String statusCodeToken = UUID.randomUUID().toString();

    private String environmentVariablesToken = UUID.randomUUID().toString();

    private ShellStdOutLogger sshStdOutLogger;

    private ShellStdErrLogger sshStdErrLogger;

    private Future<?> stdOutFuture;

    private Future<?> stdErrFuture;

    private String node;

    private String operation;

    private String scriptName;

    private ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {

        private AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("ShellOutputConsumer" + count.incrementAndGet());
            return t;
        }
    });

    public ShellOutputHandler(String statusCodeToken, String environmentVariablesToken, String node, String operation, String scriptName) {
        this.statusCodeToken = statusCodeToken;
        this.environmentVariablesToken = environmentVariablesToken;
        this.node = node;
        this.operation = operation;
        this.scriptName = scriptName;
    }

    @Override
    public void handleStdOut(InputStream stdOut) {
        sshStdOutLogger = new ShellStdOutLogger(node, operation, scriptName, log, statusCodeToken, environmentVariablesToken, stdOut);
        stdOutFuture = executorService.submit(sshStdOutLogger);
    }

    @Override
    public void handleStdErr(InputStream stdErr) {
        sshStdErrLogger = new ShellStdErrLogger(operation, scriptName, log, stdErr);
        stdErrFuture = executorService.submit(sshStdErrLogger);
    }

    @Override
    public OperationOutput getOperationOutput() throws ExecutionException, InterruptedException {
        waitForOutputToBeConsumed();
        return new OperationOutput(sshStdOutLogger.getStatusCode(), sshStdOutLogger.getCapturedEnvVars());
    }

    @Override
    public OperationOutput tryGetOperationOutput() {
        if (sshStdOutLogger.getStatusCode() != null) {
            return new OperationOutput(sshStdOutLogger.getStatusCode(), sshStdOutLogger.getCapturedEnvVars());
        } else {
            return null;
        }
    }

    @Override
    public void waitForOutputToBeConsumed() throws ExecutionException, InterruptedException {
        stdErrFuture.get();
        stdOutFuture.get();
    }

    @Override
    public void close() {
        executorService.shutdown();
    }
}
