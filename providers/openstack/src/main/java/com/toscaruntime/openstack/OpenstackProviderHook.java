package com.toscaruntime.openstack;

import com.toscaruntime.configuration.ProviderConnectionRegistry;
import com.toscaruntime.openstack.nodes.Compute;
import com.toscaruntime.openstack.nodes.ExternalNetwork;
import com.toscaruntime.openstack.nodes.Network;
import com.toscaruntime.openstack.nodes.Volume;
import com.toscaruntime.sdk.AbstractProviderHook;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.util.PropertyUtil;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class OpenstackProviderHook extends AbstractProviderHook {

    private ProviderConnectionRegistry<OpenstackProviderConnection> connectionRegistry;

    @Override
    public void postConstruct(Deployment deployment, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> bootstrapContext) {
        connectionRegistry = new ProviderConnectionRegistry<>(pluginProperties, bootstrapContext, new OpenstackProviderConnectionFactory());
    }

    private String getNodeTarget(Map<String, Object> nodeProperties) {
        return PropertyUtil.getPropertyAsString(nodeProperties, "providers.openstack.target", "default");
    }

    @Override
    public void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        Set<ExternalNetwork> externalNetworks = DeploymentUtil.getNodeInstancesByType(nodeInstances, ExternalNetwork.class);
        Set<Compute> computes = DeploymentUtil.getNodeInstancesByType(nodeInstances, Compute.class);
        Set<Network> networks = DeploymentUtil.getNodeInstancesByType(nodeInstances, Network.class);
        Set<Volume> volumes = DeploymentUtil.getNodeInstancesByType(nodeInstances, Volume.class);
        for (ExternalNetwork externalNetwork : externalNetworks) {
            OpenstackProviderConnection connection = connectionRegistry.getConnection(getNodeTarget(externalNetwork.getProperties()));
            externalNetwork.setNetworkApi(connection.getNetworkApi());
        }
        for (Compute compute : computes) {
            compute.setConnection(connectionRegistry.getConnection(getNodeTarget(compute.getProperties())));
            Set<ExternalNetwork> connectedExternalNetworks = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, compute.getId(), tosca.relationships.Network.class, ExternalNetwork.class);
            Set<Network> connectedInternalNetworks = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, compute.getId(), tosca.relationships.Network.class, Network.class);
            compute.setNetworks(connectedInternalNetworks);
            compute.setExternalNetworks(connectedExternalNetworks);
            Set<Volume> attachedVolumes = DeploymentUtil.getSourceInstancesOfRelationship(relationshipInstances, compute.getId(), tosca.relationships.AttachTo.class, Volume.class);
            compute.setVolumes(attachedVolumes);
        }
        for (Network network : networks) {
            OpenstackProviderConnection connection = connectionRegistry.getConnection(getNodeTarget(network.getProperties()));
            network.setConnection(connection);
            network.setExternalNetworks(externalNetworks);
        }
        for (Volume volume : volumes) {
            OpenstackProviderConnection connection = connectionRegistry.getConnection(getNodeTarget(volume.getProperties()));
            volume.setConnection(connection);
        }
    }
}
