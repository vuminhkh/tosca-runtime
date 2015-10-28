package com.mkv.tosca.openstack;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jclouds.ContextBuilder;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.NeutronApiMetadata;
import org.jclouds.openstack.neutron.v2.extensions.RouterApi;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;
import org.jclouds.openstack.neutron.v2.features.SubnetApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.NovaApiMetadata;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.mkv.exception.ProviderInitializationException;
import com.mkv.tosca.openstack.nodes.Compute;
import com.mkv.tosca.openstack.nodes.ExternalNetwork;
import com.mkv.tosca.openstack.nodes.Network;
import com.mkv.tosca.sdk.Deployment;
import com.mkv.tosca.sdk.DeploymentPostConstructor;
import com.mkv.util.PropertyUtil;

public class OpenstackDeploymentPostConstructor implements DeploymentPostConstructor {

    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties) {
        Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
        String keystoneUrl = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "keystone_url");
        String tenant = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "tenant");
        String user = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "user");
        String password = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "password");
        String region = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "region");
        String externalNetworkId = PropertyUtil.getPropertyAsString(providerProperties, "external_network_id");
        Properties overrideProperties = PropertyUtil.toProperties(providerProperties, "keystone_url", "tenant", "user", "password", "region");
        NovaApi novaApi = ContextBuilder
                .newBuilder(new NovaApiMetadata())
                .endpoint(keystoneUrl)
                .credentials(tenant + ":" + user, password)
                .modules(modules)
                .overrides(overrideProperties)
                .buildApi(NovaApi.class);

        NeutronApi neutronApi = ContextBuilder
                .newBuilder(new NeutronApiMetadata())
                .endpoint(keystoneUrl)
                .credentials(tenant + ":" + user, password)
                .modules(modules)
                .overrides(overrideProperties)
                .buildApi(NeutronApi.class);

        if (!novaApi.getConfiguredRegions().contains(region)) {
            throw new ProviderInitializationException("Nova : Region " + region + " do not exist, available regions are " + novaApi.getConfiguredRegions());
        }
        if (!neutronApi.getConfiguredRegions().contains(region)) {
            throw new ProviderInitializationException("Neutron : Region " + region + " do not exist, available regions are " + neutronApi.getConfiguredRegions());
        }
        ServerApi serverApi = novaApi.getServerApi(region);
        NetworkApi networkApi = neutronApi.getNetworkApi(region);
        SubnetApi subnetApi = neutronApi.getSubnetApi(region);
        FloatingIPApi floatingIPApi = novaApi.getFloatingIPApi(region).get();
        RouterApi routerApi = neutronApi.getRouterApi(region).get();
        Set<Compute> computes = deployment.getNodeInstancesByType(Compute.class);
        for (Compute compute : computes) {
            compute.setServerApi(serverApi);
            compute.setExternalNetworkId(externalNetworkId);
            compute.setFloatingIPApi(floatingIPApi);
            Set<ExternalNetwork> connectedExternalNetworks = deployment.getNodeInstancesByRelationship(compute.getId(), tosca.relationships.Network.class, ExternalNetwork.class);
            Set<Network> connectedInternalNetworks = deployment.getNodeInstancesByRelationship(compute.getId(), tosca.relationships.Network.class, Network.class);
            compute.setNetworks(connectedInternalNetworks);
            compute.setExternalNetworks(connectedExternalNetworks);
        }
        Set<ExternalNetwork> externalNetworks = deployment.getNodeInstancesByType(ExternalNetwork.class);
        Set<Network> networks = deployment.getNodeInstancesByType(Network.class);
        for (Network network : networks) {
            network.setNetworkApi(networkApi);
            network.setSubnetApi(subnetApi);
            network.setExternalNetworks(externalNetworks);
            network.setExternalNetworkId(externalNetworkId);
            network.setRouterApi(routerApi);
        }
    }
}
