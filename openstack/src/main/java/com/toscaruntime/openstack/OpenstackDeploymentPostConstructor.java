package com.toscaruntime.openstack;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jclouds.ContextBuilder;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.cinder.v1.CinderApiMetadata;
import org.jclouds.openstack.cinder.v1.features.VolumeApi;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.NeutronApiMetadata;
import org.jclouds.openstack.neutron.v2.extensions.RouterApi;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;
import org.jclouds.openstack.neutron.v2.features.SubnetApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.NovaApiMetadata;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeAttachmentApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.toscaruntime.exception.ProviderInitializationException;
import com.toscaruntime.openstack.nodes.Compute;
import com.toscaruntime.openstack.nodes.ExternalNetwork;
import com.toscaruntime.openstack.nodes.Network;
import com.toscaruntime.openstack.nodes.Volume;
import com.toscaruntime.openstack.util.NetworkUtil;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.DeploymentPostConstructor;
import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.util.PropertyUtil;

import tosca.nodes.Root;

public class OpenstackDeploymentPostConstructor implements DeploymentPostConstructor {

    private ServerApi serverApi;

    private NetworkApi networkApi;

    private SubnetApi subnetApi;

    private FloatingIPApi floatingIPApi;

    private RouterApi routerApi;

    private String networkId;

    private String externalNetworkId;

    private VolumeApi volumeApi;

    private VolumeAttachmentApi volumeAttachmentApi;

    private String getNetworkIdFromContext(NetworkApi networkApi, Map<String, String> providerProperties, Map<String, Object> bootstrapContext, boolean isExternal) {
        String networkIdPropertyName;
        String networkNamePropertyName;
        if (isExternal) {
            networkIdPropertyName = "external_network_id";
            networkNamePropertyName = "external_network_name";
        } else {
            networkIdPropertyName = "network_id";
            networkNamePropertyName = "network_name";
        }
        String networkId = PropertyUtil.getPropertyAsString(providerProperties, networkIdPropertyName,
                PropertyUtil.getPropertyAsString(bootstrapContext, networkIdPropertyName));
        if (StringUtils.isEmpty(networkId)) {
            String networkName = PropertyUtil.getPropertyAsString(providerProperties, networkNamePropertyName,
                    PropertyUtil.getPropertyAsString(bootstrapContext, networkNamePropertyName));
            org.jclouds.openstack.neutron.v2.domain.Network found = NetworkUtil.findNetworkByName(networkApi, networkName, isExternal);
            if (found != null) {
                return found.getId();
            }
        }
        return networkId;
    }

    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext) {
        Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
        String keystoneUrl = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "keystone_url");
        String tenant = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "tenant");
        String user = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "user");
        String password = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "password");
        String region = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "region");
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

        CinderApi cinderApi = ContextBuilder
                .newBuilder(new CinderApiMetadata())
                .endpoint(keystoneUrl)
                .credentials(tenant + ":" + user, password)
                .modules(modules)
                .overrides(overrideProperties)
                .buildApi(CinderApi.class);

        if (!novaApi.getConfiguredRegions().contains(region)) {
            throw new ProviderInitializationException("Nova : Region " + region + " do not exist, available regions are " + novaApi.getConfiguredRegions());
        }
        if (!neutronApi.getConfiguredRegions().contains(region)) {
            throw new ProviderInitializationException("Neutron : Region " + region + " do not exist, available regions are " + neutronApi.getConfiguredRegions());
        }
        serverApi = novaApi.getServerApi(region);
        networkApi = neutronApi.getNetworkApi(region);
        subnetApi = neutronApi.getSubnetApi(region);
        floatingIPApi = novaApi.getFloatingIPApi(region).get();
        routerApi = neutronApi.getRouterApi(region).get();
        volumeApi = cinderApi.getVolumeApi(region);
        volumeAttachmentApi = novaApi.getVolumeAttachmentApi(region).get();

        /**
         * Network Id and External Network Id if defined are default values that will be injected into every compute
         * We search first in provider configuration, if not found then we'll look into bootstrap context
         */
        networkId = getNetworkIdFromContext(networkApi, providerProperties, bootstrapContext, false);
        externalNetworkId = getNetworkIdFromContext(networkApi, providerProperties, bootstrapContext, true);
        postConstructInstances(deployment.getNodeInstances(), deployment.getRelationshipInstances());
    }

    @Override
    public void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        Set<ExternalNetwork> externalNetworks = DeploymentUtil.getNodeInstancesByType(nodeInstances, ExternalNetwork.class);
        Set<Compute> computes = DeploymentUtil.getNodeInstancesByType(nodeInstances, Compute.class);
        Set<Network> networks = DeploymentUtil.getNodeInstancesByType(nodeInstances, Network.class);
        Set<Volume> volumes = DeploymentUtil.getNodeInstancesByType(nodeInstances, Volume.class);
        for (ExternalNetwork externalNetwork : externalNetworks) {
            externalNetwork.setNetworkApi(networkApi);
        }
        for (Compute compute : computes) {
            compute.setServerApi(serverApi);
            if (StringUtils.isNotBlank(externalNetworkId)) {
                compute.setExternalNetworkId(externalNetworkId);
            }
            compute.setNetworkId(networkId);
            compute.setFloatingIPApi(floatingIPApi);
            Set<ExternalNetwork> connectedExternalNetworks = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, compute.getId(), tosca.relationships.Network.class, ExternalNetwork.class);
            Set<Network> connectedInternalNetworks = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, compute.getId(), tosca.relationships.Network.class, Network.class);
            compute.setNetworks(connectedInternalNetworks);
            compute.setExternalNetworks(connectedExternalNetworks);
            compute.getChildren().stream().filter(child -> child instanceof Volume).forEach(child -> ((Volume) child).setOwner(compute));
        }
        for (Network network : networks) {
            network.setNetworkApi(networkApi);
            network.setSubnetApi(subnetApi);
            network.setExternalNetworks(externalNetworks);
            network.setExternalNetworkId(externalNetworkId);
            network.setRouterApi(routerApi);
        }
        for (Volume volume : volumes) {
            volume.setVolumeApi(volumeApi);
            volume.setVolumeAttachmentApi(volumeAttachmentApi);
        }
    }
}
