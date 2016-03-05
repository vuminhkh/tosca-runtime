package com.toscaruntime.openstack.nodes;

import org.apache.commons.lang.StringUtils;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;

import com.toscaruntime.exception.deployment.execution.ProviderResourcesNotFoundException;
import com.toscaruntime.openstack.util.NetworkUtil;

/**
 * An external network is more of an existing external network and so the life cycle is not implemented.
 */
public class ExternalNetwork extends tosca.nodes.Network {

    private NetworkApi networkApi;

    private org.jclouds.openstack.neutron.v2.domain.Network network;

    public void setNetworkApi(NetworkApi networkApi) {
        this.networkApi = networkApi;
    }

    @Override
    public void initialLoad() {
        super.initialLoad();
        this.network = networkApi.get(getAttributeAsString("provider_resource_id"));
    }

    @Override
    public void create() {
        super.create();
        String networkId = getPropertyAsString("network_id");
        if (StringUtils.isBlank(networkId)) {
            String networkName = getMandatoryPropertyAsString("network_name");
            network = NetworkUtil.findNetworkByName(networkApi, networkName, true);
            if (network == null) {
                throw new ProviderResourcesNotFoundException("ExternalNetwork [" + getId() + "] : Network with name [" + networkName + "] cannot be found");
            }
        } else {
            networkApi.get(networkId);
            if (network == null) {
                throw new ProviderResourcesNotFoundException("ExternalNetwork [" + getId() + "] : Network with id [" + networkId + "] cannot be found");
            }
        }
        setAttribute("provider_resource_id", network.getId());
        setAttribute("provider_resource_name", network.getName());
    }

    @Override
    public void delete() {
        super.delete();
        network = null;
        removeAttribute("provider_resource_id");
        removeAttribute("provider_resource_name");
    }

    public String getNetworkId() {
        if (network != null) {
            return network.getId();
        } else {
            return getPropertyAsString("network_id");
        }
    }
}
