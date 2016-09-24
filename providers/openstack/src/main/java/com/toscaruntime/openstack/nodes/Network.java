package com.toscaruntime.openstack.nodes;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.toscaruntime.exception.deployment.execution.ProviderResourcesNotFoundException;
import com.toscaruntime.openstack.OpenstackProviderConnection;
import com.toscaruntime.openstack.util.NetworkUtil;
import com.toscaruntime.util.FailSafeUtil;
import org.apache.commons.lang.StringUtils;
import org.jclouds.openstack.neutron.v2.domain.ExternalGatewayInfo;
import org.jclouds.openstack.neutron.v2.domain.Router;
import org.jclouds.openstack.neutron.v2.domain.Subnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class Network extends com.toscaruntime.common.nodes.Network {

    private static final Logger log = LoggerFactory.getLogger(Network.class);

    private String networkId;

    private String subnetId;

    private OpenstackProviderConnection connection;

    private org.jclouds.openstack.neutron.v2.domain.Network createdNetwork;

    private Subnet createdSubnet;

    private Set<Router> createdRouters = Sets.newHashSet();

    private Set<ExternalNetwork> externalNetworks;

    public void setExternalNetworks(Set<ExternalNetwork> externalNetworks) {
        this.externalNetworks = externalNetworks;
    }

    public void setConnection(OpenstackProviderConnection connection) {
        this.connection = connection;
    }

    public String getNetworkId() {
        return networkId;
    }

    public String getSubnetId() {
        return subnetId;
    }

    @Override
    public void initialLoad() {
        super.initialLoad();
        this.networkId = getAttributeAsString("provider_resource_id");
        this.subnetId = getAttributeAsString("subnet_id");
        if (StringUtils.isNotBlank(this.networkId)) {
            this.createdNetwork = connection.getNetworkApi().get(this.networkId);
        }
        if (StringUtils.isNotBlank(this.subnetId)) {
            this.createdSubnet = connection.getSubnetApi().get(this.subnetId);
        }
        List<String> createdRouterIds = (List<String>) getAttribute("created_router_ids");
        if (createdRouterIds != null) {
            this.createdRouters.addAll(createdRouterIds.stream().map(createdRouterId -> connection.getRouterApi().get(createdRouterId)).collect(Collectors.toList()));
        }
    }

    private Router createRouter(String routerName, String externalNetworkId) {
        Router router = connection.getRouterApi().create(Router.createBuilder().name(routerName).externalGatewayInfo(ExternalGatewayInfo.builder().networkId(externalNetworkId).build()).build());
        connection.getRouterApi().addInterfaceForSubnet(router.getId(), this.getSubnetId());
        this.createdRouters.add(router);
        return router;
    }

    private void initializeWithExistingNetwork(org.jclouds.openstack.neutron.v2.domain.Network existing) {
        this.networkId = existing.getId();
        if (existing.getSubnets() != null && !existing.getSubnets().isEmpty()) {
            this.subnetId = existing.getSubnets().iterator().next();
        } else {
            throw new ProviderResourcesNotFoundException("Network [" + networkId + "] : Network does not have any subnet");
        }
        setAttribute("provider_resource_name", existing.getName());
        setAttribute("subnet_id", this.subnetId);
        log.info("Network [{}] : Reuse existing network [{}] with subnet [{}]", getId(), this.networkId, this.subnetId);
    }

    @Override
    public synchronized void create() {
        super.create();
        String networkId = getPropertyAsString("network_id");
        List<String> dnsNameServers = (List<String>) getProperty("dns_name_servers");
        if (StringUtils.isBlank(networkId)) {
            String networkName = getMandatoryPropertyAsString("network_name");
            org.jclouds.openstack.neutron.v2.domain.Network existing = NetworkUtil.findNetworkByName(connection.getNetworkApi(), networkName, false);
            if (existing != null) {
                initializeWithExistingNetwork(existing);
            } else {
                String cidr = getPropertyAsString("cidr");
                int ipVersion = Integer.parseInt(getPropertyAsString("ip_version", "4"));
                this.createdNetwork = connection.getNetworkApi().create(org.jclouds.openstack.neutron.v2.domain.Network.createBuilder(networkName).build());
                this.networkId = this.createdNetwork.getId();
                Subnet.CreateBuilder subnetCreateBuilder =
                        Subnet.createBuilder(this.createdNetwork.getId(), cidr)
                                .ipVersion(ipVersion)
                                .name(networkName + "-Subnet");
                if (dnsNameServers != null) {
                    subnetCreateBuilder.dnsNameServers(ImmutableSet.copyOf(dnsNameServers));
                }
                this.createdSubnet = connection.getSubnetApi().create(subnetCreateBuilder.build());
                this.subnetId = this.createdSubnet.getId();
                // Check that we won't create twice router for the same external network
                Set<String> createdRouters = new HashSet<>();
                if (StringUtils.isNotBlank(connection.getExternalNetworkId()) && !this.externalNetworks.stream().anyMatch(nw -> connection.getExternalNetworkId().equals(nw.getNetworkId()))) {
                    // If no external network is found then use the default one if configured
                    createdRouters.add(createRouter(networkName + "-Router", connection.getExternalNetworkId()).getId());
                }
                createdRouters.addAll(externalNetworks.stream().map(externalNetwork -> createRouter(networkName + "-" + externalNetwork.getName() + "-Router", externalNetwork.getNetworkId()).getId()).collect(Collectors.toList()));
                setAttribute("created_router_ids", new ArrayList<>(createdRouters));
                setAttribute("created_network_id", this.createdNetwork.getId());
                setAttribute("created_subnet_id", this.createdSubnet.getId());
                setAttribute("provider_resource_name", networkName);
                setAttribute("subnet_id", this.subnetId);
                log.info("Network [{}] : Created network [{}] with subnet [{}]", getId(), this.networkId, this.subnetId);
            }
        } else {
            org.jclouds.openstack.neutron.v2.domain.Network existing = connection.getNetworkApi().get(networkId);
            if (existing == null) {
                throw new ProviderResourcesNotFoundException("Network [" + getId() + "] : network with id [" + networkId + "] cannot be found");
            }
            initializeWithExistingNetwork(existing);
        }
        setAttribute("provider_resource_id", this.networkId);
    }

    @Override
    public void delete() {
        super.delete();
        int retryNumber = getOperationRetry();
        long coolDownPeriod = getWaitBetweenOperationRetry();
        try {
            FailSafeUtil.doActionWithRetry(() -> {
                for (Router router : createdRouters) {
                    connection.getRouterApi().removeInterfaceForSubnet(router.getId(), createdSubnet.getId());
                    connection.getRouterApi().delete(router.getId());
                }
            }, "delete routers", retryNumber, coolDownPeriod, TimeUnit.SECONDS, Throwable.class);
        } catch (Throwable e) {
            log.warn("Could not delete routers " + createdRouters, e);
        }
        if (createdSubnet != null) {
            try {
                FailSafeUtil.doActionWithRetry(() -> connection.getSubnetApi().delete(createdSubnet.getId()), "delete subnet " + createdSubnet.getName(), retryNumber, coolDownPeriod, TimeUnit.SECONDS, Throwable.class);
            } catch (Throwable e) {
                log.warn("Network [" + getId() + "] : Could not delete subnet [" + this.createdSubnet + "]", e);
            }
        }
        if (createdNetwork != null) {
            try {
                FailSafeUtil.doActionWithRetry(() -> connection.getNetworkApi().delete(createdNetwork.getId()), "delete network " + createdNetwork.getName(), retryNumber, coolDownPeriod, TimeUnit.SECONDS, Throwable.class);
            } catch (Throwable e) {
                log.warn("Network [" + getId() + "] : Could not delete network [" + this.createdNetwork + "]", e);
            }
        }
        log.info("Network [{}] : Deleted network [{}] with subnet [{}]", getId(), this.networkId, this.subnetId);
        removeAttribute("created_router_ids");
        removeAttribute("created_network_id");
        removeAttribute("created_subnet_id");
        removeAttribute("subnet_id");
        removeAttribute("provider_resource_id");
        removeAttribute("provider_resource_name");
    }
}
