package com.toscaruntime.sdk.model;

import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.sdk.PluginHook;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This hold deployment's configuration, which is shared with nodes and relationships
 *
 * @author Minh Khang VU
 */
public class DeploymentConfig {

    /**
     * Name of the deployment
     */
    private String deploymentName;

    /**
     * Properties to initialize the providers, it can be openstack account information, docker url etc ...
     */
    private Map<String, String> providerProperties;

    /**
     * Inputs of the deployment
     */
    private Map<String, Object> inputs = new HashMap<>();

    /**
     * The bootstrap context hold information about the context of the daemon application server (id of network on openstack etc ...)
     */
    private Map<String, Object> bootstrapContext;

    /**
     * Path to the deployment recipe (which must contain artifacts, provider dependencies, deployment, provider configurations ...)
     */
    private Path recipePath;

    /**
     * Path to the tosca artifacts (scripts, binaries etc ...)
     */
    private Path artifactsPath;

    /**
     * In bootstrap mode, each VM will more likely be assigned a floating ip in order to be accessible from exterior
     */
    private boolean bootstrap;

    /**
     * Name of the csar that contains the topology from whom this deployment has been generated
     */
    private Path topologyResourcePath;

    private DeploymentPersister deploymentPersister;

    private List<PluginHook> pluginHooks;

    public Map<String, String> getProviderProperties() {
        return providerProperties;
    }

    public void setProviderProperties(Map<String, String> providerProperties) {
        this.providerProperties = providerProperties;
    }

    public String getDeploymentName() {
        return deploymentName;
    }

    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    public Path getRecipePath() {
        return recipePath;
    }

    public void setRecipePath(Path recipePath) {
        this.recipePath = recipePath;
    }

    public Path getArtifactsPath() {
        return artifactsPath;
    }

    public void setArtifactsPath(Path artifactsPath) {
        this.artifactsPath = artifactsPath;
    }

    public boolean isBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(boolean bootstrap) {
        this.bootstrap = bootstrap;
    }

    public Path getTopologyResourcePath() {
        return topologyResourcePath;
    }

    public void setTopologyResourcePath(Path topologyResourcePath) {
        this.topologyResourcePath = topologyResourcePath;
    }

    public Map<String, Object> getBootstrapContext() {
        return bootstrapContext;
    }

    public void setBootstrapContext(Map<String, Object> bootstrapContext) {
        this.bootstrapContext = bootstrapContext;
    }

    public DeploymentPersister getDeploymentPersister() {
        return deploymentPersister;
    }

    public void setDeploymentPersister(DeploymentPersister deploymentPersister) {
        this.deploymentPersister = deploymentPersister;
    }

    public List<PluginHook> getPluginHooks() {
        return pluginHooks;
    }

    public void setPluginHooks(List<PluginHook> pluginHooks) {
        this.pluginHooks = pluginHooks;
    }
}
