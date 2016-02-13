package com.toscaruntime.openstack.nodes;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import com.toscaruntime.exception.ProviderResourcesNotFoundException;
import com.toscaruntime.openstack.util.FailSafeConfigUtil;
import com.toscaruntime.openstack.util.NetworkUtil;
import com.toscaruntime.util.FailSafeUtil;

@SuppressWarnings("unchecked")
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

    private void initializeWithExistingNetwork(org.jclouds.openstack.neutron.v2.domain.Network existing) {
        this.networkId = existing.getId();
        if (existing.getSubnets() != null && !existing.getSubnets().isEmpty()) {
            this.subnetId = existing.getSubnets().iterator().next();
        } else {
            throw new ProviderResourcesNotFoundException("Network [" + networkId + "] : Network does not have any subnet");
        }
        setAttribute("provider_resource_name", existing.getName());
        log.info("Network [{}] : Reuse existing network [{}] with subnet [{}]", getId(), this.networkId, this.subnetId);
    }

    // FIXME This method is only implemented because a VM needs its private network created before it can be created
    // FIXME as the current workflow create components in concurrence event if there are relationships between them, the compute needs to wait with this method before being created
    // FIXME Needs to think about letting the provider handle workflows of native components instead of force them to use the default workflow
    public synchronized boolean waitForNetworkCreated(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (networkId != null) {
            return true;
        } else {
            wait(timeUnit.toMillis(timeout));
            return networkId != null;
        }
    }

    @Override
    public synchronized void create() {
        try {
            super.create();
            String networkId = getPropertyAsString("network_id");
            List<String> dnsNameServers = (List<String>) getProperty("dns_name_servers");
            if (StringUtils.isBlank(networkId)) {
                String networkName = getMandatoryPropertyAsString("network_name");
                org.jclouds.openstack.neutron.v2.domain.Network existing = NetworkUtil.findNetworkByName(networkApi, networkName, false);
                if (existing != null) {
                    initializeWithExistingNetwork(existing);
                } else {
                    String cidr = getPropertyAsString("cidr");
                    int ipVersion = Integer.parseInt(getPropertyAsString("ip_version", "4"));
                    this.createdNetwork = networkApi.create(org.jclouds.openstack.neutron.v2.domain.Network.createBuilder(networkName).build());
                    this.networkId = this.createdNetwork.getId();
                    Subnet.CreateBuilder subnetCreateBuilder =
                            Subnet.createBuilder(this.createdNetwork.getId(), cidr)
                                    .ipVersion(ipVersion)
                                    .name(networkName + "-Subnet");
                    if (dnsNameServers != null) {
                        subnetCreateBuilder.dnsNameServers(ImmutableSet.copyOf(dnsNameServers));
                    }
                    this.createdSubnet = subnetApi.create(subnetCreateBuilder.build());
                    this.subnetId = this.createdSubnet.getId();
                    // Check that we won't create twice router for the same external network
                    if (StringUtils.isNotBlank(externalNetworkId) && !this.externalNetworks.stream().anyMatch(nw -> externalNetworkId.equals(nw.getNetworkId()))) {
                        // If no external network is found then use the default one if configured
                        createRouter(networkName + "-Router", externalNetworkId);
                    }
                    for (ExternalNetwork externalNetwork : externalNetworks) {
                        createRouter(networkName + "-" + externalNetwork.getName() + "-Router", externalNetwork.getNetworkId());
                    }
                    setAttribute("provider_resource_name", networkName);
                    log.info("Network [{}] : Created network [{}] with subnet [{}]", getId(), this.networkId, this.subnetId);
                }
            } else {
                org.jclouds.openstack.neutron.v2.domain.Network existing = networkApi.get(networkId);
                if (existing == null) {
                    throw new ProviderResourcesNotFoundException("Network [" + getId() + "] : network with id [" + networkId + "] cannot be found");
                }
                initializeWithExistingNetwork(existing);
            }
            setAttribute("provider_resource_id", this.networkId);
        } finally {
            notifyAll();
        }
    }

    @Override
    public void delete() {
        super.delete();
        int retryNumber = getOpenstackOperationRetry();
        long coolDownPeriod = getOpenstackWaitBetweenOperationRetry();
        try {
            FailSafeUtil.doActionWithRetry(() -> {
                for (Router router : createdRouters) {
                    routerApi.removeInterfaceForSubnet(router.getId(), createdSubnet.getId());
                    routerApi.delete(router.getId());
                }
            }, "delete routers", retryNumber, coolDownPeriod, TimeUnit.SECONDS, Throwable.class);
        } catch (Throwable e) {
            log.warn("Could not delete routers " + createdRouters, e);
        }
        if (createdSubnet != null) {
            try {
                FailSafeUtil.doActionWithRetry(() -> {
                    subnetApi.delete(createdSubnet.getId());
                }, "delete subnet " + createdSubnet.getName(), retryNumber, coolDownPeriod, TimeUnit.SECONDS, Throwable.class);
            } catch (Throwable e) {
                log.warn("Network [" + getId() + "] : Could not delete subnet [" + this.createdSubnet + "]", e);
            }
        }
        if (createdNetwork != null) {
            try {
                FailSafeUtil.doActionWithRetry(() -> {
                    networkApi.delete(createdNetwork.getId());
                }, "delete network " + createdNetwork.getName(), retryNumber, coolDownPeriod, TimeUnit.SECONDS, Throwable.class);
            } catch (Throwable e) {
                log.warn("Network [" + getId() + "] : Could not delete network [" + this.createdNetwork + "]", e);
            }
        }
        log.info("Network [{}] : Deleted network [{}] with subnet [{}]", getId(), this.networkId, this.subnetId);
    }

    public int getOpenstackOperationRetry() {
        return FailSafeConfigUtil.getOpenstackOperationRetry(getProperties());
    }

    public long getOpenstackWaitBetweenOperationRetry() {
        return FailSafeConfigUtil.getOpenstackWaitBetweenOperationRetry(getProperties());
    }
}
