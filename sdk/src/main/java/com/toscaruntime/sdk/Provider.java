package com.toscaruntime.sdk;

import java.util.List;
import java.util.Map;

/**
 * This represents a provider that will be used to initialize the deployment
 */
public class Provider {

    /**
     * Map from target's name to its properties
     */
    private Map<String, Map<String, Object>> providerProperties;

    /**
     * List of provider hooks which initialize the deployment
     */
    private List<ProviderHook> providerHooks;

    public Provider(Map<String, Map<String, Object>> providerProperties, List<ProviderHook> providerHooks) {
        this.providerProperties = providerProperties;
        this.providerHooks = providerHooks;
    }

    public Map<String, Map<String, Object>> getProviderProperties() {
        return providerProperties;
    }

    public List<ProviderHook> getProviderHooks() {
        return providerHooks;
    }
}
