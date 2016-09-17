package com.toscaruntime.aws;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.toscaruntime.util.PropertyUtil;
import org.jclouds.ContextBuilder;
import org.jclouds.aws.ec2.AWSEC2Api;
import org.jclouds.ec2.features.ElasticIPAddressVPCApi;
import org.jclouds.ec2.features.InstanceApi;
import org.jclouds.ec2.features.TagApi;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

import java.util.Map;

public class AWSProviderConnection {

    private InstanceApi instanceApi;

    private ElasticIPAddressVPCApi elasticIPAddressApi;

    private TagApi tagApi;

    public AWSProviderConnection(Map<String, Object> providerProperties, Map<String, Object> bootstrapContext) {
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

    public InstanceApi getInstanceApi() {
        return instanceApi;
    }

    public ElasticIPAddressVPCApi getElasticIPAddressApi() {
        return elasticIPAddressApi;
    }

    public TagApi getTagApi() {
        return tagApi;
    }
}
