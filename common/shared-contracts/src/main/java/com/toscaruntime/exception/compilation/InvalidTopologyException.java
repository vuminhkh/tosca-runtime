package com.toscaruntime.exception.compilation;

/**
 * This indicate that the topology that is being generated is invalid
 *
 * @author Minh Khang VU
 */
public class InvalidTopologyException extends GenerationException {

    public InvalidTopologyException(String message) {
        super(message);
    }
}
