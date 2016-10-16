package com.toscaruntime.docker;

import com.toscaruntime.common.ProviderUtil;
import com.toscaruntime.configuration.ConnectionRegistry;
import com.toscaruntime.docker.nodes.Container;
import com.toscaruntime.docker.nodes.Network;
import com.toscaruntime.docker.nodes.Volume;
import com.toscaruntime.sdk.AbstractProviderHook;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.util.DeploymentUtil;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class DockerProviderHook extends AbstractProviderHook {

    private ConnectionRegistry<DockerProviderConnection> connectionRegistry;

    @Override
    public void postConstruct(Deployment deployment, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> bootstrapContext) {
        connectionRegistry = new ConnectionRegistry<>(pluginProperties, bootstrapContext, new DockerProviderConnectionFactory());
    }

    @Override
    public void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        if (nodeInstances != null) {
            for (Container container : DeploymentUtil.getNodeInstancesByType(nodeInstances, Container.class)) {
                DockerProviderConnection connection = ProviderUtil.newConnection(connectionRegistry, container);
                Set<Network> connectedNetworks = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, container.getId(), tosca.relationships.Network.class, Network.class);
                Set<Volume> attachedVolumes = DeploymentUtil.getSourceInstancesOfRelationship(relationshipInstances, container.getId(), tosca.relationships.AttachTo.class, Volume.class);
                container.setConnection(connection);
                container.setNetworks(connectedNetworks);
                container.setVolumes(attachedVolumes);
                attachedVolumes.forEach(volume -> volume.setContainer(container));
            }
            for (Network network : DeploymentUtil.getNodeInstancesByType(nodeInstances, Network.class)) {
                DockerProviderConnection connection = ProviderUtil.newConnection(connectionRegistry, network);
                network.setDockerClient(connection.getDockerClient());
            }
            for (Volume volume : DeploymentUtil.getNodeInstancesByType(nodeInstances, Volume.class)) {
                DockerProviderConnection connection = ProviderUtil.newConnection(connectionRegistry, volume);
                volume.setDockerClient(connection.getDockerClient());
            }
        }
    }
}
