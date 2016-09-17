package com.toscaruntime.docker;

import com.toscaruntime.configuration.ProviderConnectionRegistry;
import com.toscaruntime.docker.nodes.Container;
import com.toscaruntime.docker.nodes.Network;
import com.toscaruntime.docker.nodes.Volume;
import com.toscaruntime.sdk.AbstractProviderHook;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.util.PropertyUtil;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class DockerProviderHook extends AbstractProviderHook {

    private ProviderConnectionRegistry<DockerProviderConnection> connectionRegistry;

    @Override
    public void postConstruct(Deployment deployment, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> bootstrapContext) {
        connectionRegistry = new ProviderConnectionRegistry<>(pluginProperties, bootstrapContext, new DockerProviderConnectionFactory());
    }

    @Override
    public void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        if (nodeInstances != null) {
            for (Container container : DeploymentUtil.getNodeInstancesByType(nodeInstances, Container.class)) {
                String target = PropertyUtil.getPropertyAsString(container.getProperties(), "providers.docker.target", "default");
                DockerProviderConnection connection = connectionRegistry.getConnection(target);
                Set<Network> connectedNetworks = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, container.getId(), tosca.relationships.Network.class, Network.class);
                Set<Volume> attachedVolumes = DeploymentUtil.getSourceInstancesOfRelationship(relationshipInstances, container.getId(), tosca.relationships.AttachTo.class, Volume.class);
                container.setDockerClient(connection.getDockerClient());
                container.setBootstrapNetworkId(connection.getDockerNetworkId());
                container.setBootstrapNetworkName(connection.getDockerNetworkName());
                container.setNetworks(connectedNetworks);
                container.setDockerDaemonIP(connection.getDockerDaemonIP());
                container.setSwarmNodesIPsMappings(connection.getSwarmNodesIPsMappings());
                container.setVolumes(attachedVolumes);
                attachedVolumes.forEach(volume -> volume.setContainer(container));
            }
            for (Network network : DeploymentUtil.getNodeInstancesByType(nodeInstances, Network.class)) {
                String target = PropertyUtil.getPropertyAsString(network.getProperties(), "providers.docker.target", "default");
                DockerProviderConnection connection = connectionRegistry.getConnection(target);
                network.setDockerClient(connection.getDockerClient());
            }
            for (Volume volume : DeploymentUtil.getNodeInstancesByType(nodeInstances, Volume.class)) {
                String target = PropertyUtil.getPropertyAsString(volume.getProperties(), "providers.docker.target", "default");
                DockerProviderConnection connection = connectionRegistry.getConnection(target);
                volume.setDockerClient(connection.getDockerClient());
            }
        }
    }
}
