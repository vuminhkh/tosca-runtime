package com.toscaruntime.sdk.model;

import java.nio.file.Path;
import java.util.Map;

/**
 * This hold deployment's configuration
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
    private Map<String, Object> inputs;

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
}
