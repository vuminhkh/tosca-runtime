package com.toscaruntime.sdk;

import java.util.List;
import java.util.Map;

/**
 * This represents a plugin that will be used to modify / enrich the deployment
 */
public class Plugin {

    /**
     * Map from target's name to its properties
     */
    private Map<String, Map<String, Object>> pluginProperties;

    /**
     * List of plugin hooks which initialize the deployment
     */
    private List<PluginHook> pluginHooks;

    public Plugin(Map<String, Map<String, Object>> pluginProperties, List<PluginHook> pluginHooks) {
        this.pluginProperties = pluginProperties;
        this.pluginHooks = pluginHooks;
    }

    public Map<String, Map<String, Object>> getPluginProperties() {
        return pluginProperties;
    }

    public List<PluginHook> getPluginHooks() {
        return pluginHooks;
    }
}
