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
    protected void initializeNodes() {
        Map<String, Object> propertiesCompute = new HashMap<>();
        propertiesCompute.put("image_id", "toscaruntime/ubuntu-trusty");
        propertiesCompute.put("tag", "latest");
        propertiesCompute.put("interactive", "true");
        propertiesCompute.put("commands", Lists.newArrayList("bash", "-l"));
        propertiesCompute.put("exposed_ports", Lists.<Map<String, Object>>newArrayList(ImmutableMap.<String, Object>builder().put("port", "80").build()));
        propertiesCompute.put("port_mappings", Lists.<Map<String, Object>>newArrayList(ImmutableMap.<String, Object>builder().put("from", "80").put("to", "50000").build()));

        initializeNode("Compute", Container.class, null, null, propertiesCompute, new HashMap<>());


        Map<String, Object> propertiesNetwork = new HashMap<>();
        propertiesNetwork.put("network_name", "dockerNet");
        propertiesNetwork.put("cidr", "10.67.79.0/24");

        initializeNode("Network", Network.class, null, null, propertiesNetwork, new HashMap<>());

        Map<String, Object> propertiesVolume = new HashMap<>();
        propertiesVolume.put("volume_id", "toscaruntimeTestVolume");
        propertiesVolume.put("location", "/var/toscaruntimeTestVolume");

        initializeNode("Volume", DeletableVolume.class, null, null, propertiesVolume, new HashMap<>());

        setDependencies("Compute", "Network");
        setDependencies("Volume", "Compute");
    }

    @Override
    protected void initializeInstances() {

        for (int computeIndex = 1; computeIndex <= 1; computeIndex++) {
            Container compute = new Container();
            initializeInstance(compute, "Compute", computeIndex, null, null);

            for (int volumeIndex = 1; volumeIndex <= 1; volumeIndex++) {
                DeletableVolume volume = new DeletableVolume();
                initializeInstance(volume, "Volume", volumeIndex, compute, null);
            }
        }

        for (int networkIndex = 1; networkIndex <= 1; networkIndex++) {
            Network network = new Network();
            initializeInstance(network, "Network", networkIndex, null, null);
        }

    }

    @Override
    protected void postInitializeConfig() {
        this.config.setTopologyResourcePath(this.config.getArtifactsPath());
    }

    @Override
    protected void initializeRelationships() {
        generateRelationships("Compute", "Network", new HashMap<>(), tosca.relationships.Network.class);
        generateRelationships("Volume", "Compute", new HashMap<>(), tosca.relationships.AttachTo.class);
    }

    @Override
    protected void initializeRelationshipInstances() {
        generateRelationshipInstances("Compute", "Network", tosca.relationships.Network.class);
        generateRelationshipInstances("Volume", "Compute", tosca.relationships.AttachTo.class);
    }
}
