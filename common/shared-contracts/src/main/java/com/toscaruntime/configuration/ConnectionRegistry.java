package com.toscaruntime.configuration;

import com.toscaruntime.exception.deployment.configuration.TargetConfigurationNotFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * A lazily populated provider connections for all targets.
 *
 * @param <T> type of the provider connection
 */
public class ConnectionRegistry<T> {

    private Map<String, Map<String, Object>> targetConfigurations;

    private Map<String, Object> bootstrapContext;

    private ConnectionFactory<T> connectionFactory;

    private Map<ConnectionProperties, T> connectionCache;

    public ConnectionRegistry(Map<String, Map<String, Object>> targetConfigurations, Map<String, Object> bootstrapContext, ConnectionFactory<T> connectionFactory) {
        this.targetConfigurations = targetConfigurations;
        this.bootstrapContext = bootstrapContext;
        this.connectionFactory = connectionFactory;
        this.connectionCache = new HashMap<>();
    }

    public synchronized T getConnection(String target, Map<String, Object> overrideProperties) {
        Map<String, Object> targetConfiguration = targetConfigurations.get(target);
        Map<String, Object> finalConfiguration;
        if (targetConfiguration == null) {
            if (overrideProperties != null) {
                finalConfiguration = overrideProperties;
            } else {
                throw new TargetConfigurationNotFoundException("Target " + target + " is not configured and properties are not overridden at node level");
            }
        } else {
            finalConfiguration = new HashMap<>();
            finalConfiguration.putAll(targetConfiguration);
            if (overrideProperties != null) {
                finalConfiguration.putAll(overrideProperties);
            }
        }
        ConnectionProperties configurationWrapper = new ConnectionProperties(finalConfiguration);
        T connection = connectionCache.get(configurationWrapper);
        if (connection != null) {
            return connection;
        } else {
            // In multiple target for the same provider configuration, information from bootstrap context might not be used
            // For example the network openstack that was used to bootstrap may not be reachable from all the targets
            connection = this.connectionFactory.newConnection(finalConfiguration, bootstrapContext, targetConfigurations.size() > 1 || overrideProperties != null);
            this.connectionCache.put(configurationWrapper, connection);
        }
        return connection;
    }
}
