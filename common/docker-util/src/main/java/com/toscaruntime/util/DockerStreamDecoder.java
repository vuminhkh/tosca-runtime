package com.toscaruntime.util;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * Decoder for docker stream protocol
 */
public class DockerStreamDecoder {

    private DataInput dataInput;

    private StringBuilder stdinBuffer;

    private StringBuilder stdoutBuffer;

    private StringBuilder stderrBuffer;

    public DockerStreamDecoder(InputStream dockerStream) {
        this.dataInput = new DataInputStream(dockerStream);
        this.stdinBuffer = new StringBuilder();
        this.stdoutBuffer = new StringBuilder();
        this.stderrBuffer = new StringBuilder();
    }

    private byte[] readFully(int sizeToRead) throws IOException {
        byte[] buffer = new byte[sizeToRead];
        this.dataInput.readFully(buffer);
        return buffer;
    }

    private StringBuilder getBuffer(DecoderResultType streamType) throws IOException {
        switch (streamType) {
            case STD_IN:
                return stdinBuffer;
            case STD_OUT:
                return stdoutBuffer;
            case STD_ERR:
                return stderrBuffer;
            default:
                throw new IOException("Docker stream is corrupted");
        }
    }

    private DecoderResultType decodeResultType(byte streamType) throws IOException {
        switch (streamType) {
            case 0:
                return DecoderResultType.STD_IN;
            case 1:
                return DecoderResultType.STD_OUT;
            case 2:
                return DecoderResultType.STD_ERR;
            default:
                throw new IOException("Docker stream is corrupted");
        }
    }

    public List<DecoderResult> readLines() throws IOException {
        List<DecoderResult> result = Lists.newArrayList();
        while (result.isEmpty()) {
            try {
                byte streamType = dataInput.readByte();
                DecoderResultType resultType = decodeResultType(streamType);
                StringBuilder currentLine = getBuffer(resultType);
                dataInput.skipBytes(3);
                int sizeToRead = dataInput.readInt();
                String newData = new String(readFully(sizeToRead), "UTF-8");
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
                        result.add(new DecoderResult(resultType, newLine));
                    }
                } else {
                    currentLine.append(newData);
                }

            } catch (EOFException e) {
                return null;
            }
        }
        return result;
    }

    public static class DecoderResult {

        private DecoderResultType streamType;

        private String line;

        public DecoderResult(DecoderResultType streamType, String line) {
            this.streamType = streamType;
            this.line = line;
        }

        public DecoderResultType getStreamType() {
            return streamType;
        }

        public String getData() {
            return line;
        }
    }

    public enum DecoderResultType {
        STD_IN("stdin"), STD_OUT("stdout"), STD_ERR("stderr");
        private String name;

        DecoderResultType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
