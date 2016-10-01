package com.toscaruntime.openstack.nodes;

import com.toscaruntime.sdk.Deployment;

import java.util.HashMap;
import java.util.Map;

/**
 * A fake deployment for testing purpose
 *
 * @author Minh Khang VU
 */
public class OpenstackTestDeployment extends Deployment {

    @Override
    protected void addNodes() {
        Map<String, Object> propertiesCompute = new HashMap<>();
        propertiesCompute.put("image", evaluateFunction("get_input", "image"));
        propertiesCompute.put("flavor", evaluateFunction("get_input", "flavor"));
        propertiesCompute.put("key_pair_name", evaluateFunction("get_input", "key_pair_name"));
        propertiesCompute.put("security_group_names", evaluateFunction("get_input", "security_group_names"));
        Map<String, String> openstackFailSafeConfig = new HashMap<>();
        openstackFailSafeConfig.put("operation_retry", "5");
        openstackFailSafeConfig.put("wait_between_operation_retry", "5 s");
        propertiesCompute.put("provider_fail_safe", openstackFailSafeConfig);
        addNode("Compute", Compute.class.getName(), null, null, propertiesCompute, new HashMap<>());


        Map<String, Object> propertiesNetwork = new HashMap<>();
        propertiesNetwork.put("network_name", "test-network");
        propertiesNetwork.put("cidr", "192.168.1.0/24");
        propertiesNetwork.put("provider_fail_safe", openstackFailSafeConfig);
        addNode("Network", Network.class.getName(), null, null, propertiesNetwork, new HashMap<>());

        Map<String, Object> propertiesExternalNetwork = new HashMap<>();
        propertiesExternalNetwork.put("network_name", evaluateFunction("get_input", "external_network_name"));
        addNode("ExternalNetwork", ExternalNetwork.class.getName(), null, null, propertiesExternalNetwork, new HashMap<>());

        Map<String, Object> propertiesVolume = new HashMap<>();
        propertiesVolume.put("size", "1 GIB");
        propertiesVolume.put("provider_fail_safe", openstackFailSafeConfig);
        propertiesVolume.put("device", "/dev/vdc");
        addNode("Volume", DeletableVolume.class.getName(), "Compute", null, propertiesVolume, new HashMap<>());
    }

    @Override
    protected void postInitializeConfig() {
        this.config.setTopologyResourcePath(this.config.getArtifactsPath());
    }

    @Override
    public void addRelationships() {
        addRelationship("Compute", "Network", new HashMap<>(), tosca.relationships.Network.class.getName());
        addRelationship("Compute", "ExternalNetwork", new HashMap<>(), tosca.relationships.Network.class.getName());
        addRelationship("Volume", "Compute", new HashMap<>(), tosca.relationships.AttachTo.class.getName());
    }

    public java.util.Map<String, Object> getOutputs() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("compute_public_ip", evaluateFunction("get_attribute", "Compute", "public_ip_address"));
        return outputs;
    }
}
