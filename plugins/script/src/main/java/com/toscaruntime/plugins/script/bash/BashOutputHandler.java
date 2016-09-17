package com.toscaruntime.plugins.script.bash;

import com.toscaruntime.artifact.OperationOutput;
import com.toscaruntime.artifact.OutputHandler;
import com.toscaruntime.util.SSHStdErrLogger;
import com.toscaruntime.util.SSHStdOutLogger;
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

public class BashOutputHandler implements OutputHandler {

    private static final Logger log = LoggerFactory.getLogger(BashOutputHandler.class);

    private String statusCodeToken = UUID.randomUUID().toString();

    private String environmentVariablesToken = UUID.randomUUID().toString();

    private SSHStdOutLogger sshStdOutLogger;

    private SSHStdErrLogger sshStdErrLogger;

    private Future<?> stdOutFuture;

    private Future<?> stdErrFuture;

    private String operation;

    private String scriptName;

    private ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {

        private AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Bash_Executor_Thread_" + count.incrementAndGet());
            return t;
        }
    });

    public BashOutputHandler(String statusCodeToken, String environmentVariablesToken, String operation, String scriptName) {
        this.statusCodeToken = statusCodeToken;
        this.environmentVariablesToken = environmentVariablesToken;
        this.operation = operation;
        this.scriptName = scriptName;
    }

    @Override
    public void handleStdOut(InputStream stdOut) {
        sshStdOutLogger = new SSHStdOutLogger(operation, scriptName, log, statusCodeToken, environmentVariablesToken, stdOut);
        stdOutFuture = executorService.submit(sshStdOutLogger);
    }

    @Override
    public void handleStdErr(InputStream stdErr) {
        sshStdErrLogger = new SSHStdErrLogger(operation, scriptName, log, stdErr);
        stdErrFuture = executorService.submit(sshStdErrLogger);
    }

    @Override
    public OperationOutput getOperationOutput() throws ExecutionException, InterruptedException {
        stdErrFuture.get();
        stdOutFuture.get();
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
    public void close() {
        executorService.shutdown();
    }
}
