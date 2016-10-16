package com.toscaruntime.docker;

import com.toscaruntime.configuration.ConnectionFactory;

import java.util.Map;

public class DockerProviderConnectionFactory implements ConnectionFactory<DockerProviderConnection> {

    @Override
    public DockerProviderConnection newConnection(Map<String, Object> properties, Map<String, Object> bootstrapContext, boolean multipleTargets) {
        return new DockerProviderConnection(properties, bootstrapContext);
    }
}
