package com.toscaruntime.openstack.nodes;

import org.apache.commons.lang.StringUtils;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;

import com.toscaruntime.exception.ProviderResourcesNotFoundException;
import com.toscaruntime.openstack.util.NetworkUtil;

/**
 * An external network is more of an existing external network and so the life cycle is not implemented.
 */
public class ExternalNetwork extends tosca.nodes.Network {

    private NetworkApi networkApi;

    private String networkName;

    private String networkId;

    public void setNetworkApi(NetworkApi networkApi) {
        this.networkApi = networkApi;
    }

    @Override
    public void create() {
        super.create();
        networkId = getPropertyAsString("network_id");
        if (StringUtils.isEmpty(networkId)) {
            networkName = getMandatoryPropertyAsString("network_name");
            org.jclouds.openstack.neutron.v2.domain.Network openstackNetwork = NetworkUtil.findNetworkByName(networkApi, networkName, true);
            if (openstackNetwork == null) {
                throw new ProviderResourcesNotFoundException("Network with name [" + networkName + "] cannot be found");
            }
            networkId = openstackNetwork.getId();
            networkName = openstackNetwork.getName();
        }
        setAttribute("provider_resource_id", networkId);
        setAttribute("provider_resource_name", networkName);
    }

    @Override
    public void delete() {
        super.delete();
        networkId = null;
        networkName = null;
    }

    public String getNetworkId() {
        return networkId;
    }

    public String getNetworkName() {
        return networkName;
    }
}
