package com.toscaruntime.sdk;

import java.util.Map;

public interface Hook {

    /**
     * Post construct a deployment. This provides a hook to inject provider's configuration and bootstrap context's information into the provider or plugin context.
     * This method is executed just after the initialization of the deployment but before any workflow execution.
     *
     * @param deployment         the deployment to post process
     * @param providerProperties properties of the provider
     * @param bootstrapContext   bootstrap information
     */
    void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext);
}
