package com.toscaruntime.artifact;

import java.io.Closeable;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

public interface OutputHandler extends Closeable {
    /**
     * Handle Standard output
     *
     * @param stdOut the standard output
     */
    void handleStdOut(InputStream stdOut);

    /**
     * Handle the Standard Error output
     *
     * @param stdErr the standard error
     */
    void handleStdErr(InputStream stdErr);

    /**
     * Retrieve the outputs in a blocking manner
     *
     * @return the captured outputs
     */
    OperationOutput getOperationOutput() throws ExecutionException, InterruptedException;

    /**
     * Try to retrieve the outputs in a non blocking manner
     *
     * @return the captured outputs
     */
    OperationOutput tryGetOperationOutput();

    void close();
}
