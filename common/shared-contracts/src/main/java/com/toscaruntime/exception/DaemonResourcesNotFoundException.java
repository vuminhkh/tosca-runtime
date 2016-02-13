package com.toscaruntime.exception;

/**
 * Tosca runtime resources that are expected to be deployed on docker daemon are not found
 *
 * @author Minh Khang VU
 */
public class DaemonResourcesNotFoundException extends ToscaRuntimeClientException {

    public DaemonResourcesNotFoundException(String message) {
        super(message);
    }

    public DaemonResourcesNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
