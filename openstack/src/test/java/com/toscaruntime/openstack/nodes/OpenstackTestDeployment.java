package com.toscaruntime.openstack.nodes;

import java.util.HashMap;
import java.util.Map;

import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.util.PropertyUtil;

/**
 * A fake deployment for testing purpose
 *
 * @author Minh Khang VU
 */
public class OpenstackTestDeployment extends Deployment {

    @Override
    protected void initializeNodes() {
        Map<String, Object> propertiesCompute = new HashMap<>();
        propertiesCompute.put("image", "cb6b7936-d2c5-4901-8678-c88b3a6ed84c");
        propertiesCompute.put("flavor", "3");
        propertiesCompute.put("key_path", "toscaruntime.pem");
        propertiesCompute.put("login", "ubuntu");
        propertiesCompute.put("key_pair_name", "toscaruntime");
        propertiesCompute.put("security_group_names", PropertyUtil.toList("[\"default\"]"));

        initializeNode("Compute", Compute.class, null, null, propertiesCompute, new HashMap<>());


        Map<String, Object> propertiesNetwork = new HashMap<>();
        propertiesNetwork.put("network_name", "test-network");
        propertiesNetwork.put("cidr", "192.168.1.0/24");
        propertiesNetwork.put("dns_name_servers", PropertyUtil.toList("[\"8.8.8.8\"]"));

        initializeNode("Network", Network.class, null, null, propertiesNetwork, new HashMap<>());

        Map<String, Object> propertiesExternalNetwork = new HashMap<>();
        propertiesExternalNetwork.put("network_name", "public");

        initializeNode("ExternalNetwork", ExternalNetwork.class, null, null, propertiesExternalNetwork, new HashMap<>());

        setDependencies("Compute", "Network");
        setDependencies("Compute", "ExternalNetwork");
    }

    @Override
    protected void initializeInstances() {

        for (int computeIndex = 1; computeIndex <= 1; computeIndex++) {
            Compute compute = new Compute();
            initializeInstance(compute, "Compute", computeIndex, null, null);
        }

        for (int networkIndex = 1; networkIndex <= 1; networkIndex++) {
            Network network = new Network();
            initializeInstance(network, "Network", networkIndex, null, null);
        }

        for (int externalNetworkIndex = 1; externalNetworkIndex <= 1; externalNetworkIndex++) {
            ExternalNetwork externalNetwork = new ExternalNetwork();
            initializeInstance(externalNetwork, "ExternalNetwork", externalNetworkIndex, null, null);
        }

    }

    @Override
    protected void initializeRelationshipInstances() {
        generateRelationshipInstances("Compute", "Network", tosca.relationships.Network.class);
        generateRelationshipInstances("Compute", "ExternalNetwork", tosca.relationships.Network.class);
    }

    @Override
    protected void postInitializeConfig() {
        this.config.setTopologyResourcePath(this.config.getArtifactsPath());
    }

    @Override
    public void initializeRelationships() {
        generateRelationships("Compute", "Network", new HashMap<>(), tosca.relationships.Network.class);
        generateRelationships("Compute", "ExternalNetwork", new HashMap<>(), tosca.relationships.Network.class);
    }

    public java.util.Map<String, Object> getOutputs() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("compute_public_ip", evaluateFunction("get_attribute", "Compute", "public_ip_address"));
        return outputs;
    }
}
