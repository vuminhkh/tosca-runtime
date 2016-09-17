package com.toscaruntime.util;

import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.async.ResultCallbackTemplate;
import com.toscaruntime.exception.UnexpectedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Decoder for docker stream protocol
 */
public class DockerStreamDecoder extends ResultCallbackTemplate<DockerStreamDecoder, Frame> {

    private PipedInputStream stdOut;

    private PipedOutputStream stdOutWriter;

    private PipedInputStream stdErr;

    private PipedOutputStream stdErrWriter;

    public DockerStreamDecoder() {
        try {
            this.stdErrWriter = new PipedOutputStream();
            this.stdErr = new PipedInputStream();
            this.stdErr.connect(this.stdErrWriter);
            this.stdOut = new PipedInputStream();
            this.stdOutWriter = new PipedOutputStream();
            this.stdOut.connect(this.stdOutWriter);
        } catch (IOException e) {
            throw new UnexpectedException("Could not create piped stream", e);
        }
    }

    private void closeStreams() {
        try {
            this.stdOutWriter.close();
            this.stdErrWriter.close();
        } catch (IOException e) {
            throw new UnexpectedException("Could not close piped stream", e);
        }
    }

    @Override
    public void onComplete() {
        super.onComplete();
        closeStreams();
    }

    @Override
    public void onError(Throwable throwable) {
        super.onError(throwable);
        closeStreams();
    }

    @Override
    public void onNext(Frame frame) {
        try {
            switch (frame.getStreamType()) {
                case RAW:
                case STDOUT:
                    this.stdOutWriter.write(frame.getPayload());
                    break;
                default:
                    this.stdErrWriter.write(frame.getPayload());
                    break;
            }
        } catch (IOException e) {
            throw new UnexpectedException("Could not read frame", e);
        }
    }

    public InputStream getStdOutStream() {
        return this.stdOut;
    }

    public InputStream getStdErrStream() {
        return this.stdErr;
    }
}
