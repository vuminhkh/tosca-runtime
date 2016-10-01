package com.toscaruntime.aws.nodes;

import com.toscaruntime.sdk.Deployment;

import java.util.HashMap;
import java.util.Map;

public class AWSTestDeployment extends Deployment {

    @Override
    protected void addNodes() {
        Map<String, Object> propertiesCompute = new HashMap<>();
        propertiesCompute.put("image_id", evaluateFunction("get_input", "image_id"));
        propertiesCompute.put("instance_type", evaluateFunction("get_input", "instance_type"));
        propertiesCompute.put("key_name", evaluateFunction("get_input", "key_name"));
        propertiesCompute.put("security_groups", evaluateFunction("get_input", "security_groups"));
        Map<String, String> awsFailSafeConfig = new HashMap<>();
        awsFailSafeConfig.put("operation_retry", "5");
        awsFailSafeConfig.put("wait_between_operation_retry", "5 s");
        propertiesCompute.put("provider_fail_safe", awsFailSafeConfig);
        addNode("Compute", Instance.class.getName(), null, null, propertiesCompute, new HashMap<>());

        Map<String, Object> propertiesExternalNetwork = new HashMap<>();
        addNode("ExternalNetwork", PublicNetwork.class.getName(), null, null, propertiesExternalNetwork, new HashMap<>());
    }

    @Override
    protected void postInitializeConfig() {
        this.config.setTopologyResourcePath(this.config.getArtifactsPath());
    }

    @Override
    public void addRelationships() {
        addRelationship("Compute", "ExternalNetwork", new HashMap<>(), tosca.relationships.Network.class.getName());
    }

    public java.util.Map<String, Object> getOutputs() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("compute_public_ip", evaluateFunction("get_attribute", "Compute", "public_ip_address"));
        return outputs;
    }
}
