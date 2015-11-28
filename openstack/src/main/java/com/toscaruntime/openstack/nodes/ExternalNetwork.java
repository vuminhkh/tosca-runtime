package com.toscaruntime.openstack.nodes;

/**
 * An external network is more of an existing external network and so the life cycle is not implemented.
 */
public class ExternalNetwork extends tosca.nodes.Network {

    @Override
    public void create() {
        super.create();
        getAttributes().put("tosca_id", getProperty("network_id"));
    }

    public String getNetworkId() {
        return getMandatoryProperty("network_id");
    }
}
