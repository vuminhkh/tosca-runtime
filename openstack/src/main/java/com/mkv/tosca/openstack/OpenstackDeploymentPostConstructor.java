package com.mkv.tosca.openstack;

import java.util.Map;
import java.util.Set;

import org.jclouds.ContextBuilder;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.nova.v2_0.NovaApi;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.mkv.exception.ProviderInitializationException;
import com.mkv.tosca.openstack.nodes.OpenstackCompute;
import com.mkv.tosca.sdk.Deployment;
import com.mkv.tosca.sdk.DeploymentPostConstructor;
import com.mkv.util.PropertyUtil;

public class OpenstackDeploymentPostConstructor implements DeploymentPostConstructor {

    @Override
    public void postConstruct(Deployment deployment, Map<String, Object> providerProperties) {
        Iterable<Module> modules = ImmutableSet.<Module> of(new SLF4JLoggingModule());
        String keystoneUrl = PropertyUtil.getPropertyAsString(providerProperties, "keystone_url");
        String tenant = PropertyUtil.getPropertyAsString(providerProperties, "tenant");
        String user = PropertyUtil.getPropertyAsString(providerProperties, "user");
        String password = PropertyUtil.getPropertyAsString(providerProperties, "password");
        String region = PropertyUtil.getPropertyAsString(providerProperties, "region");
        NovaApi novaApi = ContextBuilder.newBuilder("openstack-nova").endpoint(keystoneUrl).credentials(tenant + ":" + user, password).modules(modules)
                .overrides(PropertyUtil.toProperties(providerProperties, "keystone_url", "tenant", "user", "password", "region")).buildApi(NovaApi.class);
        if (!novaApi.getConfiguredRegions().contains(region)) {
            throw new ProviderInitializationException("Region " + region + " do not exist, available regions are " + novaApi.getConfiguredRegions());
        }
        Set<OpenstackCompute> computes = deployment.getNodeInstancesByNodeType(OpenstackCompute.class);
        for (OpenstackCompute compute : computes) {
            compute.setNovaApi(novaApi);
        }
    }
}
