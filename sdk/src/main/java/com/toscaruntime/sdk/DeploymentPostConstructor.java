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
     * Post construct created instances for the deployment. This method is used to post construct newly created instances for the deployment.
     *
     * @param nodeInstances         nodes to initialize
     * @param relationshipInstances relationships to initialize
     */
    void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances);
}
