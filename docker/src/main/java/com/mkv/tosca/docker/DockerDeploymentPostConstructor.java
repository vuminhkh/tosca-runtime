package com.mkv.tosca.docker;

import java.util.Map;
import java.util.Set;

import com.github.dockerjava.api.DockerClient;
import com.mkv.tosca.docker.nodes.Container;
import com.mkv.tosca.sdk.Deployment;
import com.mkv.tosca.sdk.DeploymentPostConstructor;
import com.mkv.tosca.util.DockerUtil;

/**
 * This represents a docker deployment which must hold a docker client and inject this instance in all container in order to process the execution of workflows
 * 
 * @author Minh Khang VU
 */
public class DockerDeploymentPostConstructor implements DeploymentPostConstructor {

    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties) {
        DockerClient dockerClient = DockerUtil.buildDockerClient(providerProperties);
        Set<Container> containers = deployment.getNodeInstancesByType(Container.class);
        for (Container container : containers) {
            container.setDockerClient(dockerClient);
        }
    }
}
