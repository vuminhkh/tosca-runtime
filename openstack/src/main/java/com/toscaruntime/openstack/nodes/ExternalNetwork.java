package com.toscaruntime.openstack.nodes;

/**
 * An external network is more of an existing external network and so the life cycle is not implemented.
 */
public class ExternalNetwork extends tosca.nodes.Network {

    @Override
    public void create() {
        super.create();
        setAttribute("tosca_id", getPropertyAsString("network_id"));
    }

    public String getNetworkId() {
        return getMandatoryPropertyAsString("network_id");
    }
}
