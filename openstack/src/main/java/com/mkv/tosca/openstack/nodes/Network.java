package com.mkv.tosca.openstack.nodes;

import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jclouds.openstack.neutron.v2.domain.ExternalGatewayInfo;
import org.jclouds.openstack.neutron.v2.domain.Router;
import org.jclouds.openstack.neutron.v2.domain.Subnet;
import org.jclouds.openstack.neutron.v2.extensions.RouterApi;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;
import org.jclouds.openstack.neutron.v2.features.SubnetApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mkv.tosca.exception.ResourcesNotFoundException;

public class Network extends tosca.nodes.Network {

    private static final Logger log = LoggerFactory.getLogger(Network.class);

    private NetworkApi networkApi;

    private SubnetApi subnetApi;

    private RouterApi routerApi;

    private String networkId;

    private String subnetId;

    private String externalNetworkId;

    private org.jclouds.openstack.neutron.v2.domain.Network createdNetwork;

    private Subnet createdSubnet;

    private Set<Router> createdRouters = Sets.newHashSet();

    private Set<ExternalNetwork> externalNetworks;

    public void setExternalNetworks(Set<ExternalNetwork> externalNetworks) {
        this.externalNetworks = externalNetworks;
    }

    public void setNetworkApi(NetworkApi networkApi) {
        this.networkApi = networkApi;
    }

    public void setSubnetApi(SubnetApi subnetApi) {
        this.subnetApi = subnetApi;
    }

    public void setExternalNetworkId(String externalNetworkId) {
        this.externalNetworkId = externalNetworkId;
    }

    public void setRouterApi(RouterApi routerApi) {
        this.routerApi = routerApi;
    }

    public String getNetworkId() {
        return networkId;
    }

    public String getSubnetId() {
        return subnetId;
    }

    private void createRouter(String routerName, String externalNetworkId) {
        Router router = routerApi.create(Router.createBuilder().name(routerName).externalGatewayInfo(ExternalGatewayInfo.builder().networkId(externalNetworkId).build()).build());
        this.routerApi.addInterfaceForSubnet(router.getId(), this.getSubnetId());
        this.createdRouters.add(router);
    }

    @Override
    public void create() {
        String networkId = getProperty("network_id");
        String dnsNameServers = getProperty("dns_name_servers");
        if (StringUtils.isBlank(networkId)) {
            String networkName = getMandatoryProperty("network_name");
            String cidr = getProperty("cidr");
            int ipVersion = Integer.parseInt(getProperty("ip_version", "4"));
            this.createdNetwork = networkApi.create(org.jclouds.openstack.neutron.v2.domain.Network.createBuilder(networkName).build());
            this.networkId = this.createdNetwork.getId();
            Subnet.CreateBuilder subnetCreateBuilder =
                    Subnet.createBuilder(this.createdNetwork.getId(), cidr)
                            .ipVersion(ipVersion)
                            .name(networkName + "-Subnet");
            if (StringUtils.isNotBlank(dnsNameServers)) {
                subnetCreateBuilder.dnsNameServers(ImmutableSet.copyOf(dnsNameServers.trim().split("\\s*,\\s*")));
            }
            this.createdSubnet = subnetApi.create(subnetCreateBuilder.build());
            this.subnetId = this.createdSubnet.getId();
            if (StringUtils.isNotBlank(externalNetworkId)) {
                createRouter(networkName + "-Router", externalNetworkId);
            }
            for (ExternalNetwork externalNetwork : externalNetworks) {
                createRouter(networkName + "-" + externalNetwork.getName() + "-Router", externalNetwork.getMandatoryProperty("network_id"));
            }
        } else {
            org.jclouds.openstack.neutron.v2.domain.Network existing = networkApi.get(networkId);
            if (existing == null) {
                throw new ResourcesNotFoundException("Network not found " + networkId);
            }
            this.networkId = existing.getId();
            if (existing.getSubnets() != null && !existing.getSubnets().isEmpty()) {
                this.subnetId = existing.getSubnets().iterator().next();
            } else {
                throw new ResourcesNotFoundException("Network " + networkId + " does not have any subnet");
            }
        }
        log.info("Created network <" + this.networkId + "> with subnet <" + this.subnetId + ">");
    }

    @Override
    public void configure() {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void delete() {
        if (this.createdSubnet != null) {
            this.subnetApi.delete(this.createdSubnet.getId());
        }
        if (this.createdNetwork != null) {
            this.networkApi.delete(this.createdNetwork.getId());
        }
        for (Router router : createdRouters) {
            this.routerApi.delete(router.getId());
        }
        log.info("Deleted network <" + this.networkId + "> with subnet <" + this.subnetId + ">");
    }
}
