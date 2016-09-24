package com.toscaruntime.aws;

import com.toscaruntime.aws.nodes.Instance;
import com.toscaruntime.aws.nodes.PublicNetwork;
import com.toscaruntime.configuration.ProviderConnectionRegistry;
import com.toscaruntime.sdk.AbstractProviderHook;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.util.PropertyUtil;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class AWSProviderHook extends AbstractProviderHook {

    private ProviderConnectionRegistry<AWSProviderConnection> connectionRegistry;

    private String getNodeTarget(Map<String, Object> nodeProperties) {
        return PropertyUtil.getPropertyAsString(nodeProperties, "providers.aws.target", "default");
    }

    @Override
    public void postConstruct(Deployment deployment, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> bootstrapContext) {
        connectionRegistry = new ProviderConnectionRegistry<>(pluginProperties, bootstrapContext, new AWSProviderConnectionFactory());
    }

    @Override
    public void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        Set<PublicNetwork> publicNetworks = DeploymentUtil.getNodeInstancesByType(nodeInstances, PublicNetwork.class);
        Set<Instance> computes = DeploymentUtil.getNodeInstancesByType(nodeInstances, Instance.class);
        computes.forEach(compute -> {
            AWSProviderConnection connection = connectionRegistry.getConnection(getNodeTarget(compute.getProperties()));
            compute.setAssociateElasticIP(!publicNetworks.isEmpty());
            compute.setConnection(connection);
        });
    }
}
