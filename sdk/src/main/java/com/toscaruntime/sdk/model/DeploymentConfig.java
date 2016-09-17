package com.toscaruntime.sdk.model;

import com.toscaruntime.artifact.Executor;
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
     * Inputs of the deployment
     */
    private Map<String, Object> inputs = new HashMap<>();

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

    /**
     * Executor's registry for the deployment
     */
    private Map<String, Class<? extends Executor>> artifactExecutorRegistry = new HashMap<>();

    public Map<String, Class<? extends Executor>> getArtifactExecutorRegistry() {
        return artifactExecutorRegistry;
    }

    public void setArtifactExecutorRegistry(Map<String, Class<? extends Executor>> artifactExecutorRegistry) {
        this.artifactExecutorRegistry = artifactExecutorRegistry;
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
