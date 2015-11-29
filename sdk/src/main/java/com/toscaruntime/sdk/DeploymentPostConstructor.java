package com.toscaruntime.sdk;

import java.util.Map;

public interface DeploymentPostConstructor {

    /**
     * Post construct a deployment. This provides a hook to inject provider's configuration and bootstrap context's information into the deployment.
     *
     * @param deployment         the deployment to post process
     * @param providerProperties properties of the provider
     * @param bootstrapContext   bootstrap information
     */
    void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext);
}
