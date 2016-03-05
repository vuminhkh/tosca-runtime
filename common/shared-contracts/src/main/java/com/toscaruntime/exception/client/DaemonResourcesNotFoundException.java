package com.toscaruntime.exception.client;

import com.toscaruntime.exception.BadUsageException;

/**
 * Tosca runtime resources that are expected to be deployed on docker daemon are not found
 *
 * @author Minh Khang VU
 */
public class DaemonResourcesNotFoundException extends BadUsageException {

    public DaemonResourcesNotFoundException(String message) {
        super(message);
    }
}
