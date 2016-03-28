package com.toscaruntime.openstack.nodes;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeAttachmentApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.toscaruntime.exception.deployment.artifact.ArtifactAuthenticationFailureException;
import com.toscaruntime.exception.deployment.artifact.ArtifactConnectException;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.exception.deployment.artifact.ArtifactInterruptedException;
import com.toscaruntime.exception.deployment.execution.InvalidOperationExecutionException;
import com.toscaruntime.openstack.util.FailSafeConfigUtil;
import com.toscaruntime.util.ArtifactExecutionUtil;
import com.toscaruntime.util.FailSafeUtil;
import com.toscaruntime.util.SSHJExecutor;
import com.toscaruntime.util.SynchronizationUtil;

@SuppressWarnings("unchecked")
public class Compute extends tosca.nodes.Compute {

    private static final Logger log = LoggerFactory.getLogger(Compute.class);

    private static final String RECIPE_LOCATION = "/tmp/recipe";

    private SSHJExecutor artifactExecutor;

    private ServerApi serverApi;

    private FloatingIPApi floatingIPApi;

    private VolumeAttachmentApi volumeAttachmentApi;

    private String networkId;

    private String externalNetworkId;

    private Set<ExternalNetwork> externalNetworks;

    private Set<Network> networks;

    private Set<Volume> volumes;

    private Server server;

    private String ipAddress;

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public void setServerApi(ServerApi serverApi) {
        this.serverApi = serverApi;
    }

    public void setFloatingIPApi(FloatingIPApi floatingIPApi) {
        this.floatingIPApi = floatingIPApi;
    }

    public void setExternalNetworkId(String externalNetworkId) {
        this.externalNetworkId = externalNetworkId;
    }

    public void setExternalNetworks(Set<ExternalNetwork> externalNetworks) {
        this.externalNetworks = externalNetworks;
    }

    public void setNetworks(Set<Network> networks) {
        this.networks = networks;
    }

    public void setVolumes(Set<Volume> volumes) {
        this.volumes = volumes;
    }

    public void setVolumeAttachmentApi(VolumeAttachmentApi volumeAttachmentApi) {
        this.volumeAttachmentApi = volumeAttachmentApi;
    }

    @Override
    public void initialLoad() {
        super.initialLoad();
        this.server = serverApi.get(getAttributeAsString("provider_resource_id"));
        this.ipAddress = getAttributeAsString("ip_address");
        this.artifactExecutor = createExecutor(getIpAddressForSSHSession());
        this.artifactExecutor.initialize();
    }

    @Override
    public void uploadRecipe() {
        String recipeLocation = getPropertyAsString("recipe_location", RECIPE_LOCATION);
        artifactExecutor.upload(this.config.getArtifactsPath().toString(), recipeLocation);
    }

    @Override
    public Map<String, String> execute(String nodeId, String operationArtifactPath, Map<String, Object> inputs, Map<String, String> deploymentArtifacts) {
        String recipeLocation = getPropertyAsString("recipe_location", RECIPE_LOCATION);
        try {
            return FailSafeUtil.doActionWithRetry(
                    () -> artifactExecutor.executeArtifact(
                            nodeId,
                            config.getArtifactsPath().resolve(operationArtifactPath),
                            recipeLocation + "/" + operationArtifactPath,
                            ArtifactExecutionUtil.processInputs(inputs, deploymentArtifacts, recipeLocation, "/")
                    ),
                    operationArtifactPath,
                    getArtifactExecutionRetry(),
                    getWaitBetweenArtifactExecutionRetry(),
                    TimeUnit.SECONDS,
                    ArtifactConnectException.class,
                    ArtifactExecutionException.class);
        } catch (ArtifactInterruptedException e) {
            log.info("Compute [{}][{}] has been interrupted", getId(), operationArtifactPath);
            throw e;
        } catch (Throwable e) {
            throw new InvalidOperationExecutionException("Compute [" + getId() + "] : Unable to execute operation " + operationArtifactPath, e);
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

        int retryNumber = getOpenstackOperationRetry();
        long coolDownPeriod = getOpenstackWaitBetweenOperationRetry();

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

        if (StringUtils.isNotEmpty(this.networkId)) {
            internalNetworks.add(this.networkId);
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
        FailSafeUtil.doActionWithRetry(() -> {
            ServerCreated serverCreated = this.serverApi.create(config.getDeploymentName().replaceAll("[^\\p{L}\\p{Nd}]+", "") + "_" + this.getId(), getMandatoryPropertyAsString("image"), getMandatoryPropertyAsString("flavor"), createServerOptions);
            this.server = serverApi.get(serverCreated.getId());
        }, "Create compute " + getId(), retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        log.info("Compute [{}] : Created server with id [{}]", getId(), this.server);
    }

    private void destroySshExecutor() {
        if (artifactExecutor != null) {
            artifactExecutor.close();
        }
    }

    private SSHJExecutor createExecutor(String ipForSSSHSession) {
        String user = getMandatoryPropertyAsString("login");
        String keyPath = getMandatoryPropertyAsString("key_path");
        String absoluteKeyPath;
        if (Paths.get(keyPath).isAbsolute()) {
            absoluteKeyPath = keyPath;
        } else {
            absoluteKeyPath = this.config.getTopologyResourcePath().resolve(keyPath).toString();
        }
        String port = getPropertyAsString("ssh_port", "22");
        // Create the executor
        return new SSHJExecutor(user, ipForSSSHSession, Integer.parseInt(port), absoluteKeyPath, Boolean.parseBoolean(getPropertyAsString("elevate_privilege")));
    }

    private void initSshExecutor(String ipForSSSHSession) {
        if (StringUtils.isBlank(ipForSSSHSession)) {
            throw new InvalidOperationExecutionException("Compute [" + getId() + "] : IP of the server " + getId() + "is null, maybe it was not initialized properly or has been deleted");
        }
        String operationName = "Create ssh session for " + getId();
        int connectRetry = getConnectRetry();
        long waitBetweenConnectRetry = getWaitBetweenConnectRetry();
        try {
            // Create the executor
            this.artifactExecutor = createExecutor(ipForSSSHSession);
            // Wait before initializing the connection
            Thread.sleep(TimeUnit.SECONDS.toMillis(getWaitBeforeConnection()));
            // Initialize the connection
            FailSafeUtil.doActionWithRetry(() -> artifactExecutor.initialize(), operationName, connectRetry, waitBetweenConnectRetry, TimeUnit.SECONDS, ArtifactConnectException.class, ArtifactAuthenticationFailureException.class);
            // Sometimes the created VM needs sometimes to initialize and to resolve DNS
            Thread.sleep(TimeUnit.SECONDS.toMillis(getWaitBeforeArtifactExecution()));
            // Upload the recipe to the remote host
            FailSafeUtil.doActionWithRetry(this::uploadRecipe, "Upload recipe", connectRetry, waitBetweenConnectRetry, TimeUnit.SECONDS, ArtifactConnectException.class);
        } catch (ArtifactInterruptedException e) {
            throw e;
        } catch (Throwable e) {
            log.error("Compute [" + getId() + "] : Unable to create ssh session", e);
            throw new InvalidOperationExecutionException("Compute [" + getId() + "] : Unable to create ssh session", e);
        }
    }

    private FloatingIP attachFloatingIP(String externalNetworkId, int retryNumber, long coolDownPeriod) {
        FloatingIP floatingIP = FailSafeUtil.doActionWithRetry(() -> this.floatingIPApi.allocateFromPool(externalNetworkId),
                "Allocation floating ip to compute " + getId(), retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        this.floatingIPApi.addToServer(floatingIP.getIp(), this.server.getId());
        setAttribute("public_ip_address", floatingIP.getIp());
        log.info("Compute [{}] : Attached floating ip [{}]", getId(), floatingIP.getIp());
        return floatingIP;
    }

    private void attachVolume(Volume volume) {
        volumeAttachmentApi.attachVolumeToServerAsDevice(volume.getVolume().getId(), server.getId(), volume.getPropertyAsString("device", ""));
        volume.waitForStatus(org.jclouds.openstack.cinder.v1.domain.Volume.Status.IN_USE);
        if (!volume.getVolume().getAttachments().isEmpty()) {
            volume.setAttribute("device", volume.getVolume().getAttachments().iterator().next().getDevice());
        }
    }

    @Override
    public void start() {
        super.start();
        int retryNumber = getOpenstackOperationRetry();
        long coolDownPeriod = getOpenstackWaitBetweenOperationRetry();
        if (this.server == null) {
            throw new InvalidOperationExecutionException("Compute [" + getId() + "] : Must create the server before starting it");
        }
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            boolean isUp = Server.Status.ACTIVE.equals(server.getStatus())
                    && server.getAddresses() != null
                    && !server.getAddresses().isEmpty();
            if (!isUp) {
                log.info("Compute [{}] : Waiting for server [{}] to be up, current state [{}]", getId(), server.getId(), server.getStatus());
                server = serverApi.get(server.getId());
                return false;
            } else {
                return true;
            }
        }, retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        this.ipAddress = server.getAddresses().values().iterator().next().getAddr();
        // Create floating ips
        Set<String> floatingIpIds = this.externalNetworks.stream().map(externalNetwork -> attachFloatingIP(externalNetwork.getNetworkId(), retryNumber, coolDownPeriod).getId()).collect(Collectors.toSet());
        if (this.config.isBootstrap() && this.externalNetworks.isEmpty() && StringUtils.isNotBlank(this.externalNetworkId)) {
            // In bootstrap mode and if external network id can be found in the context, we attach automatically a floating ip
            log.info("Compute [{}] : Bootstrap mode enabled automatically attach a floating IP from [{}] as no external network found for the compute [{}]", getId(), this.externalNetworkId);
            floatingIpIds.add(attachFloatingIP(this.externalNetworkId, retryNumber, coolDownPeriod).getId());
        }
        // Attach volumes
        this.volumes.stream().forEach(this::attachVolume);
        setAttribute("ip_address", this.ipAddress);
        setAttribute("provider_resource_id", server.getId());
        setAttribute("provider_resource_name", server.getName());
        setAttribute("floating_ip_ids", new ArrayList<>(floatingIpIds));
        initSshExecutor(getIpAddressForSSHSession());
        log.info("Compute [{}] : Started server with info [{}]", getId(), server);
    }

    private String getIpAddressForSSHSession() {
        if (!this.config.isBootstrap()) {
            return ipAddress;
        } else {
            String attachedFloatingIP = getAttributeAsString("public_ip_address");
            if (StringUtils.isBlank(attachedFloatingIP)) {
                log.warn("Compute [{}] : Bootstrap mode is enabled but no public_ip_address can be found on the compute, will use private ip [{}]", getId(), ipAddress);
                return ipAddress;
            } else {
                log.info("Compute [{}] : Bootstrap mode is enabled, use public ip [{}] to initialize SSH session", getId(), attachedFloatingIP);
                return attachedFloatingIP;
            }
        }
    }

    private void detachVolume(Volume volume) {
        try {
            FailSafeUtil.doActionWithRetry(
                    () -> volumeAttachmentApi.detachVolumeFromServer(volume.getVolume().getId(), this.server.getId()),
                    "Detach volume",
                    getOpenstackOperationRetry(),
                    getOpenstackWaitBetweenOperationRetry(),
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
        this.volumes.stream().forEach(this::detachVolume);
        // Stop the compute
        int retryNumber = getOpenstackOperationRetry();
        long coolDownPeriod = getOpenstackWaitBetweenOperationRetry();
        destroySshExecutor();
        this.serverApi.stop(this.server.getId());
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            boolean isShutOff = Server.Status.SHUTOFF.equals(server.getStatus());
            if (!isShutOff) {
                log.info("Compute [{}]: Waiting for server [{}] to be shutdown, current state [{}]", getId(), server.getId(), server.getStatus());
                server = serverApi.get(server.getId());
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
        int retryNumber = getOpenstackOperationRetry();
        long coolDownPeriod = getOpenstackWaitBetweenOperationRetry();
        List<String> createdFloatingIPs = (List<String>) getAttribute("floating_ip_ids");
        if (createdFloatingIPs != null) {
            for (String floatingIP : createdFloatingIPs) {
                this.floatingIPApi.delete(floatingIP);
            }
            this.removeAttribute("floating_ip_ids");
        }
        String serverId = this.server.getId();
        this.serverApi.delete(this.server.getId());
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            server = serverApi.get(server.getId());
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

    private int getOpenstackOperationRetry() {
        return FailSafeConfigUtil.getOpenstackOperationRetry(getProperties());
    }

    private long getOpenstackWaitBetweenOperationRetry() {
        return FailSafeConfigUtil.getOpenstackWaitBetweenOperationRetry(getProperties());
    }

    private long getWaitBeforeConnection() {
        String waitBeforeConnection = getMandatoryPropertyAsString("compute_fail_safe.wait_before_connection");
        return FailSafeConfigUtil.convertToSeconds(waitBeforeConnection);
    }

    private int getConnectRetry() {
        return Integer.parseInt(getMandatoryPropertyAsString("compute_fail_safe.connect_retry"));
    }

    private long getWaitBetweenConnectRetry() {
        String waitBetweenConnectRetry = getMandatoryPropertyAsString("compute_fail_safe.wait_between_connect_retry");
        return FailSafeConfigUtil.convertToSeconds(waitBetweenConnectRetry);
    }

    private int getArtifactExecutionRetry() {
        return Integer.parseInt(getMandatoryPropertyAsString("compute_fail_safe.artifact_execution_retry"));
    }

    private long getWaitBetweenArtifactExecutionRetry() {
        String waitBetweenArtifactExecutionRetry = getMandatoryPropertyAsString("compute_fail_safe.wait_between_artifact_execution_retry");
        return FailSafeConfigUtil.convertToSeconds(waitBetweenArtifactExecutionRetry);
    }

    private long getWaitBeforeArtifactExecution() {
        String waitBeforeArtifactExecution = getMandatoryPropertyAsString("compute_fail_safe.wait_before_artifact_execution");
        return FailSafeConfigUtil.convertToSeconds(waitBeforeArtifactExecution);
    }
}
