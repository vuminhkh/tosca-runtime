package com.toscaruntime.openstack;

import com.toscaruntime.configuration.ConnectionFactory;

import java.util.HashMap;
import java.util.Map;

public class OpenstackProviderConnectionFactory implements ConnectionFactory<OpenstackProviderConnection> {

    @Override
    public OpenstackProviderConnection newConnection(Map<String, Object> properties, Map<String, Object> bootstrapContext, boolean multipleTargets) {
        if (multipleTargets) {
            // In a multiple target configuration do not reuse network id and external network in the bootstrap context as it might change between configurations
            return new OpenstackProviderConnection(properties, new HashMap<>());
        } else {
            return new OpenstackProviderConnection(properties, bootstrapContext);
        }
    }
}
