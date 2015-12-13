package com.toscaruntime.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Base class to capture ssh output
 *
 * @author Minh Khang VU
 */
public abstract class SSHOutputStream extends OutputStream {

    protected String operationName;

    private StringBuilder currentLine = new StringBuilder();

    @Override
    public void write(int b) throws IOException {
        byte[] d = new byte[1];
        d[0] = (byte) b;
        write(d, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        String newData = new String(b, off, len, "UTF-8");
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
                handleNewLine(newLine);
            }
        } else {
            currentLine.append(newData);
        }
    }

    protected abstract void handleNewLine(String newLine);
}
