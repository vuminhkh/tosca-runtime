package com.toscaruntime.openstack;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
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
import com.toscaruntime.exception.ProviderInitializationException;
import com.toscaruntime.openstack.nodes.Compute;
import com.toscaruntime.openstack.nodes.ExternalNetwork;
import com.toscaruntime.openstack.nodes.Network;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.DeploymentPostConstructor;
import com.toscaruntime.util.PropertyUtil;

public class OpenstackDeploymentPostConstructor implements DeploymentPostConstructor {

    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext) {
        Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
        String keystoneUrl = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "keystone_url");
        String tenant = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "tenant");
        String user = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "user");
        String password = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "password");
        String region = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "region");
        /**
         * Network Id and External Network Id here are just default value that will be used in bootstrap mode or when no value is defined for the node
         * We search first in provider configuration, if not found then we'll look into bootstrap context
         */
        String networkId =
                PropertyUtil.getPropertyAsString(providerProperties, "network_id",
                        PropertyUtil.getPropertyAsString(bootstrapContext, "network_id"));
        String externalNetworkId =
                PropertyUtil.getPropertyAsString(providerProperties, "external_network_id",
                        PropertyUtil.getPropertyAsString(bootstrapContext, "external_network_id"));
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
        Set<ExternalNetwork> externalNetworks = deployment.getNodeInstancesByType(ExternalNetwork.class);
        Set<Compute> computes = deployment.getNodeInstancesByType(Compute.class);
        for (Compute compute : computes) {
            compute.setServerApi(serverApi);
            if (StringUtils.isNotBlank(externalNetworkId)) {
                compute.setExternalNetworkId(externalNetworkId);
            } else if (!externalNetworks.isEmpty()) {
                // If no external id configured try to pick one of existing external network in the deployment for bootstrap default
                compute.setExternalNetworkId(externalNetworks.iterator().next().getNetworkId());
            }
            compute.setNetworkId(networkId);
            compute.setFloatingIPApi(floatingIPApi);
            Set<ExternalNetwork> connectedExternalNetworks = deployment.getNodeInstancesByRelationship(compute.getId(), tosca.relationships.Network.class, ExternalNetwork.class);
            Set<Network> connectedInternalNetworks = deployment.getNodeInstancesByRelationship(compute.getId(), tosca.relationships.Network.class, Network.class);
            compute.setNetworks(connectedInternalNetworks);
            compute.setExternalNetworks(connectedExternalNetworks);
        }
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
