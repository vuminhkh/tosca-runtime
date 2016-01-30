package com.toscaruntime.sdk;

import java.util.Map;
import java.util.Set;

import tosca.nodes.Root;

public interface DeploymentPostConstructor {

    /**
     * Post construct a deployment. This provides a hook to inject provider's configuration and bootstrap context's information into the deployment.
     * This method is executed just after the initialization of the deployment but before any workflow execution.
     *
     * @param deployment         the deployment to post process
     * @param providerProperties properties of the provider
     * @param bootstrapContext   bootstrap information
     */
    void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext);

    /**
     * Post construct modification of a deployment (add new instances).
     *
     * @param nodes         nodes to initialize
     * @param relationships relationships to initialize
     */
    void postConstructExtension(Map<String, Root> nodes, Set<tosca.relationships.Root> relationships);
}
