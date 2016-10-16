package com.toscaruntime.aws;

import com.toscaruntime.aws.nodes.Instance;
import com.toscaruntime.aws.nodes.PublicNetwork;
import com.toscaruntime.common.ProviderUtil;
import com.toscaruntime.configuration.ConnectionRegistry;
import com.toscaruntime.sdk.AbstractProviderHook;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.util.DeploymentUtil;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class AWSProviderHook extends AbstractProviderHook {

    private ConnectionRegistry<AWSProviderConnection> connectionRegistry;

    @Override
    public void postConstruct(Deployment deployment, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> bootstrapContext) {
        connectionRegistry = new ConnectionRegistry<>(pluginProperties, bootstrapContext, new AWSProviderConnectionFactory());
    }

    @Override
    public void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        Set<PublicNetwork> publicNetworks = DeploymentUtil.getNodeInstancesByType(nodeInstances, PublicNetwork.class);
        Set<Instance> computes = DeploymentUtil.getNodeInstancesByType(nodeInstances, Instance.class);
        computes.forEach(compute -> {
            AWSProviderConnection connection = ProviderUtil.newConnection(connectionRegistry, compute);
            compute.setAssociateElasticIP(!publicNetworks.isEmpty());
            compute.setConnection(connection);
        });
    }
}
