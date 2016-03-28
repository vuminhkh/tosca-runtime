package com.toscaruntime.docker.nodes;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.toscaruntime.sdk.Deployment;

/**
 * @author Minh Khang VU
 */
public class DockerTestDeployment extends Deployment {

    @Override
    protected void addNodes() {
        Map<String, Object> propertiesCompute = new HashMap<>();
        propertiesCompute.put("image_id", "toscaruntime/ubuntu-trusty");
        propertiesCompute.put("tag", "latest");
        propertiesCompute.put("interactive", "true");
        propertiesCompute.put("commands", Lists.newArrayList("bash", "-l"));
        propertiesCompute.put("exposed_ports", Lists.<Map<String, Object>>newArrayList(ImmutableMap.<String, Object>builder().put("port", "80").build()));
        propertiesCompute.put("port_mappings", Lists.<Map<String, Object>>newArrayList(ImmutableMap.<String, Object>builder().put("from", "80").put("to", "50000").build()));

        addNode("Compute", Container.class, null, null, propertiesCompute, new HashMap<>());


        Map<String, Object> propertiesNetwork = new HashMap<>();
        propertiesNetwork.put("network_name", "dockerNet");
        propertiesNetwork.put("cidr", "10.67.79.0/24");

        addNode("Network", Network.class, null, null, propertiesNetwork, new HashMap<>());

        Map<String, Object> propertiesVolume = new HashMap<>();
        propertiesVolume.put("volume_id", "toscaruntimeTestVolume");
        propertiesVolume.put("location", "/var/toscaruntimeTestVolume");

        addNode("Volume", DeletableVolume.class, "Compute", null, propertiesVolume, new HashMap<>());
    }

    @Override
    protected void postInitializeConfig() {
        this.config.setTopologyResourcePath(this.config.getArtifactsPath());
    }

    @Override
    protected void addRelationships() {
        addRelationship("Compute", "Network", new HashMap<>(), tosca.relationships.Network.class);
        addRelationship("Volume", "Compute", new HashMap<>(), tosca.relationships.AttachTo.class);
    }
}
