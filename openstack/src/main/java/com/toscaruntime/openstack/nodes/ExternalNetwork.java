package com.toscaruntime.openstack.nodes;

/**
 * An external network is more of an existing external network and so the life cycle is not implemented.
 */
public class ExternalNetwork extends tosca.nodes.Network {

    @Override
    public void create() {
        super.create();
        setAttribute("provider_resource_id", getPropertyAsString("network_id"));
        setAttribute("provider_resource_name", getPropertyAsString("network_name"));
    }

    public String getNetworkId() {
        return getMandatoryPropertyAsString("network_id");
    }
}
