package com.toscaruntime.openstack.nodes;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.util.PropertyUtil;

/**
 * A fake deployment for testing purpose
 *
 * @author Minh Khang VU
 */
public class TestDeployment extends Deployment {

    @Override
    public void initializeDeployment(String deploymentName, Path recipePath, Map<String, Object> inputs, boolean bootstrap) {
        super.initializeDeployment(deploymentName, recipePath, inputs, bootstrap);
        this.config.setTopologyResourcePath(this.config.getArtifactsPath());

        Map<String, Object> propertiesCompute = new HashMap<>();
        propertiesCompute.put("image", "cb6b7936-d2c5-4901-8678-c88b3a6ed84c");
        propertiesCompute.put("flavor", "3");
        propertiesCompute.put("key_path", "toscaruntime.pem");
        propertiesCompute.put("login", "ubuntu");
        propertiesCompute.put("key_pair_name", "toscaruntime");
        propertiesCompute.put("security_group_names", PropertyUtil.toList("[\"default\"]"));

        initializeNode("Compute", propertiesCompute);
        for (int computeIndex = 1; computeIndex <= 1; computeIndex++) {
            Compute compute = new Compute();
            compute.setId("Compute" + computeIndex);
            compute.setName("Compute");
            compute.setProperties(propertiesCompute);
            initializeInstance(compute);
            nodeInstances.put(compute.getId(), compute);
        }

        Map<String, Object> propertiesNetwork = new HashMap<>();
        propertiesNetwork.put("network_name", "test-network");
        propertiesNetwork.put("cidr", "192.168.1.0/24");
        propertiesNetwork.put("dns_name_servers", PropertyUtil.toList("[\"8.8.8.8\"]"));

        initializeNode("Network", propertiesNetwork);
        for (int networkIndex = 1; networkIndex <= 1; networkIndex++) {
            Network network = new Network();
            network.setId("Network" + networkIndex);
            network.setName("Network");
            network.setProperties(propertiesNetwork);
            initializeInstance(network);
            nodeInstances.put(network.getId(), network);
        }

        Map<String, Object> propertiesExternalNetwork = new HashMap<>();
        propertiesExternalNetwork.put("network_name", "public");

        initializeNode("ExternalNetwork", propertiesExternalNetwork);
        for (int externalNetworkIndex = 1; externalNetworkIndex <= 1; externalNetworkIndex++) {
            ExternalNetwork externalNetwork = new ExternalNetwork();
            externalNetwork.setId("ExternalNetwork" + externalNetworkIndex);
            externalNetwork.setName("ExternalNetwork");
            externalNetwork.setProperties(propertiesExternalNetwork);
            initializeInstance(externalNetwork);
            nodeInstances.put(externalNetwork.getId(), externalNetwork);
        }

        setDependencies("Compute", "Network");
        setDependencies("Compute", "ExternalNetwork");

        generateRelationships("Compute", "Network", tosca.relationships.Network.class);
        generateRelationships("Compute", "ExternalNetwork", tosca.relationships.Network.class);
    }

    public java.util.Map<String, Object> getOutputs() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("compute_public_ip", evaluateFunction("get_attribute", "Compute", "public_ip_address"));
        return outputs;
    }
}
