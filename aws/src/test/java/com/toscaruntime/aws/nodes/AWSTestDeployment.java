package com.toscaruntime.aws.nodes;

import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.util.PropertyUtil;

import java.util.HashMap;
import java.util.Map;

public class AWSTestDeployment extends Deployment {

    @Override
    protected void addNodes() {
        Map<String, Object> propertiesCompute = new HashMap<>();
        propertiesCompute.put("image_id", "ami-47a23a30");
        propertiesCompute.put("instance_type", "t2.small");
        propertiesCompute.put("key_path", System.getenv("AWS_KEY_PATH"));
        propertiesCompute.put("login", "ubuntu");
        propertiesCompute.put("key_name", System.getenv("AWS_KEY_NAME"));
        propertiesCompute.put("security_groups", PropertyUtil.toList("[\"openbar\"]"));
        propertiesCompute.put("recipe_location", "/tmp/recipe");
        Map<String, String> failSafeConfig = new HashMap<>();
        failSafeConfig.put("connect_retry", "20");
        failSafeConfig.put("wait_between_connect_retry", "5 s");
        failSafeConfig.put("artifact_execution_retry", "1");
        failSafeConfig.put("wait_between_artifact_execution_retry", "10 s");
        failSafeConfig.put("wait_before_artifact_execution", "5 s");
        failSafeConfig.put("wait_before_connection", "5 s");
        Map<String, String> awsFailSafeConfig = new HashMap<>();
        awsFailSafeConfig.put("operation_retry", "5");
        awsFailSafeConfig.put("wait_between_operation_retry", "5 s");
        propertiesCompute.put("aws_fail_safe", awsFailSafeConfig);
        propertiesCompute.put("compute_fail_safe", failSafeConfig);
        addNode("Compute", Instance.class, null, null, propertiesCompute, new HashMap<>());

        Map<String, Object> propertiesExternalNetwork = new HashMap<>();
        propertiesExternalNetwork.put("network_name", "public");
        addNode("ExternalNetwork", PublicNetwork.class, null, null, propertiesExternalNetwork, new HashMap<>());
    }

    @Override
    protected void postInitializeConfig() {
        this.config.setTopologyResourcePath(this.config.getArtifactsPath());
    }


    @Override
    public void addRelationships() {
        addRelationship("Compute", "ExternalNetwork", new HashMap<>(), tosca.relationships.Network.class);
    }

    public java.util.Map<String, Object> getOutputs() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("compute_public_ip", evaluateFunction("get_attribute", "Compute", "public_ip_address"));
        return outputs;
    }
}
