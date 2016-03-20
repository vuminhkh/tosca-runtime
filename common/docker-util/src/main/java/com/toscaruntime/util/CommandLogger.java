package com.toscaruntime.util;

public interface CommandLogger {

    void log(DockerStreamDecoder.DecoderResult line);
}
