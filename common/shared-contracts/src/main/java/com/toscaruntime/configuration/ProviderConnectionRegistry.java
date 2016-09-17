package com.toscaruntime.configuration;

import com.toscaruntime.exception.deployment.configuration.TargetConfigurationNotFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * A lazily populated provider connections for all targets.
 *
 * @param <T> type of the provider connection
 */
public class ProviderConnectionRegistry<T> {

    private Map<String, Map<String, Object>> targetConfigurations;

    private Map<String, Object> bootstrapContext;

    private ProviderConnectionFactory<T> connectionFactory;

    private Map<String, T> connectionCache;

    public ProviderConnectionRegistry(Map<String, Map<String, Object>> targetConfigurations, Map<String, Object> bootstrapContext, ProviderConnectionFactory<T> connectionFactory) {
        this.targetConfigurations = targetConfigurations;
        this.bootstrapContext = bootstrapContext;
        this.connectionFactory = connectionFactory;
        this.connectionCache = new HashMap<>();
    }

    public synchronized T getConnection(String target) {
        T connection = connectionCache.get(target);
        if (connection != null) {
            return connection;
        } else {
            Map<String, Object> targetConfiguration = targetConfigurations.get(target);
            if (targetConfiguration == null) {
                throw new TargetConfigurationNotFoundException("Target " + target + " is not configured");
            }
            // In multiple target for the same provider configuration, information from bootstrap context might not be used
            // For example the network openstack that was used to bootstrap may not be reachable from all the targets
            connection = this.connectionFactory.newConnection(targetConfiguration, bootstrapContext, targetConfiguration.size() > 1);
            this.connectionCache.put(target, connection);
        }
        return connection;
    }
}
