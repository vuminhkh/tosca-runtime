package com.toscaruntime.exception.deployment.plugins;

import com.toscaruntime.exception.BadUsageException;

public class PluginConfigurationException extends BadUsageException {

    public PluginConfigurationException(String message) {
        super(message);
    }
}
