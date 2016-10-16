package com.toscaruntime.aws;

import com.toscaruntime.configuration.ConnectionFactory;

import java.util.Collections;
import java.util.Map;

public class AWSProviderConnectionFactory implements ConnectionFactory<AWSProviderConnection> {

    @Override
    public AWSProviderConnection newConnection(Map<String, Object> properties, Map<String, Object> bootstrapContext, boolean multipleTargets) {
        if (multipleTargets) {
            return new AWSProviderConnection(properties, Collections.emptyMap());
        } else {
            return new AWSProviderConnection(properties, bootstrapContext);
        }
    }
}
