package com.toscaruntime.aws;

import com.toscaruntime.configuration.ProviderConnectionFactory;

import java.util.Collections;
import java.util.Map;

public class AWSProviderConnectionFactory implements ProviderConnectionFactory<AWSProviderConnection> {

    @Override
    public AWSProviderConnection newConnection(Map<String, Object> properties, Map<String, Object> bootstrapContext, boolean multipleTargets) {
        if (multipleTargets) {
            return new AWSProviderConnection(properties, Collections.emptyMap());
        } else {
            return new AWSProviderConnection(properties, bootstrapContext);
        }
    }
}
