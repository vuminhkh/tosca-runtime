package com.toscaruntime.configuration;

import java.util.Map;

public interface ConnectionFactory<T> {

    /**
     * Create new connection for the provider
     *
     * @param properties       properties of the provider
     * @param bootstrapContext the bootstrap context if exist
     * @param multipleTargets  is the deployment containing multiple targets ?
     * @return the created connection
     */
    T newConnection(Map<String, Object> properties, Map<String, Object> bootstrapContext, boolean multipleTargets);
}
