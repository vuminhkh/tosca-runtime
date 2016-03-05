package com.toscaruntime.exception.deployment.configuration;

import com.toscaruntime.exception.BadUsageException;

/**
 * At runtime, a property was required but not there, it might be limit cases when tosca constraints do not help to detect this problem before in compilation phase.
 *
 * @author Minh Khang VU
 */
public class PropertyRequiredException extends BadUsageException {

    public PropertyRequiredException(String message) {
        super(message);
    }
}
