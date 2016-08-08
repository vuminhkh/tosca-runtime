package com.toscaruntime.aws;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.toscaruntime.aws.nodes.Instance;
import com.toscaruntime.aws.nodes.PublicNetwork;
import com.toscaruntime.sdk.AbstractProviderHook;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.util.PropertyUtil;
import org.jclouds.ContextBuilder;
import org.jclouds.aws.ec2.AWSEC2Api;
import org.jclouds.ec2.features.ElasticIPAddressVPCApi;
import org.jclouds.ec2.features.InstanceApi;
import org.jclouds.ec2.features.TagApi;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class AWSProviderHook extends AbstractProviderHook {

    private InstanceApi instanceApi;

    private ElasticIPAddressVPCApi elasticIPAddressApi;

    private TagApi tagApi;

    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext) {
        String accessKeyId = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "access_key_id");
        String accessKeySecret = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "access_key_secret");
        String region = PropertyUtil.getMandatoryPropertyAsString(providerProperties, "region");
        Iterable<Module> modules = ImmutableSet.of(new SLF4JLoggingModule());
        AWSEC2Api ec2Api = ContextBuilder.newBuilder("aws-ec2").credentials(accessKeyId, accessKeySecret)
                .modules(modules).buildApi(AWSEC2Api.class);
        instanceApi = ec2Api.getInstanceApiForRegion(region).get();
        elasticIPAddressApi = ec2Api.getElasticIPAddressVPCApiForRegion(region).get();
        tagApi = ec2Api.getTagApiForRegion(region).get();
    }

    @Override
    public void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        Set<PublicNetwork> publicNetworks = DeploymentUtil.getNodeInstancesByType(nodeInstances, PublicNetwork.class);
        Set<Instance> computes = DeploymentUtil.getNodeInstancesByType(nodeInstances, Instance.class);
        computes.stream().forEach(compute -> {
            compute.setAssociateElasticIP(!publicNetworks.isEmpty());
            compute.setElasticIPAddressApi(elasticIPAddressApi);
            compute.setInstanceApi(instanceApi);
            compute.setTagApi(tagApi);
        });
    }
}
