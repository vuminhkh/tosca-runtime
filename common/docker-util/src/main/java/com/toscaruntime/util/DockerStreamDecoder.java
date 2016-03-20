package com.toscaruntime.util;

import java.io.IOException;
import java.util.List;

import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.async.ResultCallbackTemplate;
import com.google.common.collect.Lists;

/**
 * Decoder for docker stream protocol
 */
public class DockerStreamDecoder extends ResultCallbackTemplate<DockerStreamDecoder, Frame> {

    private CommandLogger logger;

    private StringBuilder stdinBuffer;

    private StringBuilder stdoutBuffer;

    private StringBuilder stderrBuffer;

    DockerStreamDecoder(CommandLogger logger) {
        this.logger = logger;
        this.stdinBuffer = new StringBuilder();
        this.stdoutBuffer = new StringBuilder();
        this.stderrBuffer = new StringBuilder();
    }

    private StringBuilder getBuffer(StreamType streamType) throws IOException {
        switch (streamType) {
            case STDIN:
                return stdinBuffer;
            case RAW:
            case STDOUT:
                return stdoutBuffer;
            case STDERR:
                return stderrBuffer;
            default:
                throw new IOException("Docker stream is corrupted");
        }
    }

    @Override
    public void onNext(Frame frame) {
        List<DecoderResult> result = Lists.newArrayList();
        try {
            StringBuilder currentLine = getBuffer(frame.getStreamType());
            String newData = new String(frame.getPayload(), "UTF-8");
            if (newData.contains("\n")) {
                String[] newLines = newData.split("\n");
                if (newLines.length == 0) {
                    if (currentLine.length() > 0) {
                        newLines = new String[]{currentLine.toString()};
                        currentLine.setLength(0);
                    }
                }
                if (currentLine.length() > 0) {
                    newLines[0] = currentLine.append(newLines[0]).toString();
                    currentLine.setLength(0);
                }
                for (String newLine : newLines) {
                    result.add(new DecoderResult(frame.getStreamType(), newLine));
                }
            } else {
                currentLine.append(newData);
            }
            if (!result.isEmpty()) {
                for (DecoderResult line : result) {
                    logger.log(line);
                }
            }
        } catch (IOException e) {
            onError(e);
        }
    }

    static class DecoderResult {

        private StreamType streamType;

        private String line;

        DecoderResult(StreamType streamType, String line) {
            this.streamType = streamType;
            this.line = line;
        }

        StreamType getStreamType() {
            return streamType;
        }

        public String getData() {
            return line;
        }
    }
}
