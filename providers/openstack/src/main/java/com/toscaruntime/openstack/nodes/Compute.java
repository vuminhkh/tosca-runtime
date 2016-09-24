package com.toscaruntime.openstack.nodes;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.toscaruntime.common.nodes.LinuxCompute;
import com.toscaruntime.exception.deployment.execution.InvalidOperationExecutionException;
import com.toscaruntime.openstack.OpenstackProviderConnection;
import com.toscaruntime.util.FailSafeUtil;
import com.toscaruntime.util.SynchronizationUtil;
import org.apache.commons.lang.StringUtils;
import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class Compute extends LinuxCompute {

    private static final Logger log = LoggerFactory.getLogger(Compute.class);

    private OpenstackProviderConnection connection;

    private Set<ExternalNetwork> externalNetworks;

    private Set<Network> networks;

    private Set<Volume> volumes;

    private Server server;

    public void setExternalNetworks(Set<ExternalNetwork> externalNetworks) {
        this.externalNetworks = externalNetworks;
    }

    public void setNetworks(Set<Network> networks) {
        this.networks = networks;
    }

    public void setVolumes(Set<Volume> volumes) {
        this.volumes = volumes;
    }

    @Override
    public void initialLoad() {
        super.initialLoad();
        String serverId = getAttributeAsString("provider_resource_id");
        if (StringUtils.isNotBlank(serverId)) {
            this.server = connection.getServerApi().get(getAttributeAsString("provider_resource_id"));
        }
    }

    @Override
    public void create() {
        String userData = getPropertyAsString("user_data");
        List<String> networksProperty = (List<String>) getProperty("networks");
        List<String> securityGroups = (List<String>) getProperty("security_group_names");
        String adminPass = getPropertyAsString("admin_pass");
        String availabilityZone = getPropertyAsString("availability_zone");
        String configDrive = getPropertyAsString("config_drive");
        String diskConfig = getPropertyAsString("disk_config");
        String keyPairName = getPropertyAsString("key_pair_name");
        CreateServerOptions createServerOptions = new CreateServerOptions();

        int retryNumber = getOperationRetry();
        long coolDownPeriod = getWaitBetweenOperationRetry();

        if (StringUtils.isNotBlank(adminPass)) {
            createServerOptions.adminPass(adminPass);
        }
        if (StringUtils.isNotBlank(availabilityZone)) {
            createServerOptions.availabilityZone(availabilityZone);
        }
        if (StringUtils.isNotBlank(configDrive)) {
            createServerOptions.configDrive(Boolean.parseBoolean(configDrive));
        }
        if (StringUtils.isNotBlank(diskConfig)) {
            createServerOptions.diskConfig(diskConfig);
        }
        if (StringUtils.isNotBlank(keyPairName)) {
            createServerOptions.keyPairName(keyPairName);
        }
        if (StringUtils.isNotBlank(userData)) {
            createServerOptions.userData(userData.getBytes(Charsets.UTF_8));
        }
        Set<String> internalNetworks = Sets.newHashSet();
        if (networksProperty != null) {
            internalNetworks.addAll(networksProperty);
        }

        internalNetworks.addAll(networks.stream().map(Network::getNetworkId).collect(Collectors.toList()));

        if (StringUtils.isNotEmpty(connection.getNetworkId())) {
            internalNetworks.add(connection.getNetworkId());
        }
        if (!internalNetworks.isEmpty()) {
            createServerOptions.networks(internalNetworks);
        }
        if (securityGroups != null) {
            createServerOptions.securityGroupNames(securityGroups);
        }
        log.info("Compute [" + getId() + "] : Creating server with info:\n- AVZ: {}\n- Config drive: {}\n- Disk config: {}\n- Key pair: {}\n- Networks: {}\n- Security groups: {}\n- User data: {}",
                createServerOptions.getAvailabilityZone(),
                createServerOptions.getConfigDrive(),
                createServerOptions.getDiskConfig(),
                createServerOptions.getKeyPairName(),
                createServerOptions.getNetworks(),
                createServerOptions.getSecurityGroupNames(),
                userData);
        FailSafeUtil.doActionWithRetryNoCheckedException(() -> {
            ServerCreated serverCreated = connection.getServerApi().create(config.getDeploymentName().replaceAll("[^\\p{L}\\p{Nd}]+", "") + "_" + this.getId(), getMandatoryPropertyAsString("image"), getMandatoryPropertyAsString("flavor"), createServerOptions);
            this.server = connection.getServerApi().get(serverCreated.getId());
        }, "Create compute " + getId(), retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        log.info("Compute [{}] : Created server with id [{}]", getId(), this.server);
    }

    private FloatingIP attachFloatingIP(String externalNetworkId, int retryNumber, long coolDownPeriod) {
        FloatingIP floatingIP = FailSafeUtil.doActionWithRetryNoCheckedException(() -> connection.getFloatingIPApi().allocateFromPool(externalNetworkId),
                "Allocation floating ip to compute " + getId(), retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        connection.getFloatingIPApi().addToServer(floatingIP.getIp(), this.server.getId());
        setAttribute("public_ip_address", floatingIP.getIp());
        log.info("Compute [{}] : Attached floating ip [{}]", getId(), floatingIP.getIp());
        return floatingIP;
    }

    private void attachVolume(Volume volume) {
        connection.getVolumeAttachmentApi().attachVolumeToServerAsDevice(volume.getVolume().getId(), server.getId(), volume.getPropertyAsString("device", ""));
        volume.waitForStatus(org.jclouds.openstack.cinder.v1.domain.Volume.Status.IN_USE);
        if (!volume.getVolume().getAttachments().isEmpty()) {
            volume.setAttribute("device", volume.getVolume().getAttachments().iterator().next().getDevice());
        }
    }

    @Override
    public void start() {
        super.start();
        int retryNumber = getOperationRetry();
        long coolDownPeriod = getWaitBetweenOperationRetry();
        if (this.server == null) {
            throw new InvalidOperationExecutionException("Compute [" + getId() + "] : Must create the server before starting it");
        }
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            boolean isUp = Server.Status.ACTIVE.equals(server.getStatus())
                    && server.getAddresses() != null
                    && !server.getAddresses().isEmpty();
            if (!isUp) {
                log.info("Compute [{}] : Waiting for server [{}] to be up, current state [{}]", getId(), server.getId(), server.getStatus());
                server = connection.getServerApi().get(server.getId());
                return false;
            } else {
                return true;
            }
        }, retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        String ipAddress;
        if (StringUtils.isNotEmpty(connection.getNetworkName()) && server.getAddresses().containsKey(connection.getNetworkName())) {
            // The primary private ip address must come from configured network, in case of a bootstrap this is important as only this ip is reachable by the agent
            ipAddress = server.getAddresses().get(connection.getNetworkName()).iterator().next().getAddr();
        } else {
            ipAddress = server.getAddresses().values().iterator().next().getAddr();
        }
        Map<String, List<String>> ipAddresses = new HashMap<>();
        for (Map.Entry<String, Collection<Address>> addressEntry : server.getAddresses().asMap().entrySet()) {
            ipAddresses.put(addressEntry.getKey(), addressEntry.getValue().stream().map(Address::getAddr).collect(Collectors.toList()));
        }
        // Create floating ips
        Set<String> floatingIpIds = this.externalNetworks.stream().map(externalNetwork -> attachFloatingIP(externalNetwork.getNetworkId(), retryNumber, coolDownPeriod).getId()).collect(Collectors.toSet());
        if (this.config.isBootstrap() && this.externalNetworks.isEmpty() && StringUtils.isNotBlank(connection.getExternalNetworkId())) {
            // In bootstrap mode and if external network id can be found in the context, we attach automatically a floating ip
            log.info("Compute [{}] : Bootstrap mode enabled automatically attach a floating IP from [{}] as no external network found for the compute [{}]", getId(), connection.getExternalNetworkId());
            floatingIpIds.add(attachFloatingIP(connection.getExternalNetworkId(), retryNumber, coolDownPeriod).getId());
        }
        // Attach volumes
        this.volumes.forEach(this::attachVolume);
        setAttribute("ip_address", ipAddress);
        setAttribute("ip_addresses", ipAddresses);
        setAttribute("provider_resource_id", server.getId());
        setAttribute("provider_resource_name", server.getName());
        setAttribute("floating_ip_ids", new ArrayList<>(floatingIpIds));
        log.info("Compute [{}] : Started server with info [{}]", getId(), server);
    }

    private void detachVolume(Volume volume) {
        try {
            FailSafeUtil.doActionWithRetry(
                    () -> connection.getVolumeAttachmentApi().detachVolumeFromServer(volume.getVolume().getId(), this.server.getId()),
                    "Detach volume",
                    getOperationRetry(),
                    getWaitBetweenOperationRetry(),
                    TimeUnit.SECONDS,
                    Exception.class);
        } catch (Throwable throwable) {
            log.error("Volume [" + getId() + "] : Could not detach volume " + volume.getVolume().getId() + " from server " + this.server.getId());
        }
        volume.removeAttribute("device");
        volume.waitForStatus(org.jclouds.openstack.cinder.v1.domain.Volume.Status.AVAILABLE);
    }

    @Override
    public void stop() {
        super.stop();
        if (this.server == null) {
            log.warn("Compute [{}] : Server has not been started yet", getId());
            return;
        }
        // Detach volumes
        this.volumes.forEach(this::detachVolume);
        // Stop the compute
        int retryNumber = getOperationRetry();
        long coolDownPeriod = getWaitBetweenOperationRetry();
        connection.getServerApi().stop(this.server.getId());
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            boolean isShutOff = Server.Status.SHUTOFF.equals(server.getStatus());
            if (!isShutOff) {
                log.info("Compute [{}]: Waiting for server [{}] to be shutdown, current state [{}]", getId(), server.getId(), server.getStatus());
                server = connection.getServerApi().get(server.getId());
                return false;
            } else {
                return true;
            }
        }, retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        log.info("Compute [{}] : Stopped server with id [{}]", getId(), this.server.getId());
    }

    @Override
    public void delete() {
        super.delete();
        if (this.server == null) {
            log.warn("Compute [{}] : Server has not been started yet", getId());
            return;
        }
        int retryNumber = getOperationRetry();
        long coolDownPeriod = getWaitBetweenOperationRetry();
        List<String> createdFloatingIPs = (List<String>) getAttribute("floating_ip_ids");
        if (createdFloatingIPs != null) {
            for (String floatingIP : createdFloatingIPs) {
                connection.getFloatingIPApi().delete(floatingIP);
            }
            this.removeAttribute("floating_ip_ids");
        }
        String serverId = this.server.getId();
        connection.getServerApi().delete(this.server.getId());
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            server = connection.getServerApi().get(server.getId());
            boolean isDeleted = server == null;
            if (!isDeleted) {
                log.info("Compute [{}] : Waiting for server [{}] to be deleted, current state [{}]", getId(), server.getId(), server.getStatus());
            }
            return isDeleted;
        }, retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        log.info("Compute [{}] : Deleted server with id [{}]", getId(), serverId);
        this.removeAttribute("ip_address");
        this.removeAttribute("provider_resource_id");
        this.removeAttribute("provider_resource_name");
        this.removeAttribute("public_ip_address");
        this.removeAttribute("floating_ip_ids");
    }

    public void setConnection(OpenstackProviderConnection connection) {
        this.connection = connection;
    }
}
