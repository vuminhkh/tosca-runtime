package com.mkv.tosca.docker;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.mkv.tosca.docker.nodes.Container;
import com.mkv.tosca.sdk.Deployment;
import com.mkv.tosca.sdk.DeploymentPostConstructor;

/**
 * This represents a docker deployment which must hold a docker client and inject this instance in all container in order to process the execution of workflows
 * 
 * @author Minh Khang VU
 */
public class DockerDeploymentPostConstructor implements DeploymentPostConstructor {

    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties) {
        System.setProperty("http.maxConnections", String.valueOf(Integer.MAX_VALUE));
        Properties properties = new Properties();
        properties.putAll(providerProperties);
        DockerClientConfig config = new DockerClientConfig.DockerClientConfigBuilder().withProperties(properties).build();
        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();
        Set<Container> containers = deployment.getNodeInstancesByNodeType(Container.class);
        for (Container container : containers) {
            container.setDockerClient(dockerClient);
        }
    }
}
