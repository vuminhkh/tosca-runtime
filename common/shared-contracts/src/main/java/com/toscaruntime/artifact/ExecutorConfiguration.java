package com.toscaruntime.artifact;

import java.util.Map;

/**
 * This contains all needed information for a configuration executor + connection.
 */
public class ExecutorConfiguration {

    private Class<? extends Executor> executorType;

    private Class<? extends Connection> connectionType;

    private Map<String, Object> properties;

    public ExecutorConfiguration(Class<? extends Executor> executorType, Class<? extends Connection> connectionType, Map<String, Object> properties) {
        this.executorType = executorType;
        this.connectionType = connectionType;
        this.properties = properties;
    }

    public Class<? extends Executor> getExecutorType() {
        return executorType;
    }

    public Class<? extends Connection> getConnectionType() {
        return connectionType;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
