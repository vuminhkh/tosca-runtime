package com.mkv.tosca.sdk;

import java.util.Map;

/**
 * Post construct a deployment. This provides a hook for provider to inject specific component into the deployment.
 * 
 * @author Minh Khang VU
 */
public interface DeploymentPostConstructor {

    void postConstruct(Deployment deployment, Map<String, Object> providerProperties);
}
