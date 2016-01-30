package com.toscaruntime.openstack.nodes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.sshd.common.RuntimeSshException;
import org.apache.sshd.common.SshException;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.toscaruntime.exception.OperationExecutionException;
import com.toscaruntime.exception.ProviderResourcesNotFoundException;
import com.toscaruntime.exception.ToscaRuntimeException;
import com.toscaruntime.util.PropertyUtil;
import com.toscaruntime.util.RetryUtil;
import com.toscaruntime.util.SSHExecutor;

@SuppressWarnings("unchecked")
public class Compute extends tosca.nodes.Compute {

    private static final Logger log = LoggerFactory.getLogger(Compute.class);

    private SSHExecutor sshExecutor;

    private ServerApi serverApi;

    private FloatingIPApi floatingIPApi;

    private String networkId;

    private String externalNetworkId;

    private Set<ExternalNetwork> externalNetworks;

    private Set<Network> networks;

    private Set<FloatingIP> createdFloatingIPs = Sets.newHashSet();

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

    private Map<String, String> doExecute(String nodeId, String operationArtifactPath, Map<String, Object> inputs) {
        // Convert complex properties to JSON format before execute operation by SSH
        Map<String, String> inputTexts = new HashMap<>();
        for (Map.Entry<String, Object> input : inputs.entrySet()) {
            inputTexts.put(input.getKey(), PropertyUtil.propertyValueToString(input.getValue()));
        }
        try {
            return RetryUtil.doActionWithRetry(
                    () -> sshExecutor.executeScript(nodeId, config.getArtifactsPath().resolve(operationArtifactPath).toString(), inputTexts),
                    operationArtifactPath, 12, 30000L, RuntimeSshException.class, SshException.class
            );
        } catch (Throwable e) {
            throw new OperationExecutionException("Unable to execute operation " + operationArtifactPath, e);
        }
    }

    @Override
    public synchronized Map<String, String> execute(String nodeId, String operationArtifactPath, Map<String, Object> inputs) {
        if (this.server == null) {
            throw new ProviderResourcesNotFoundException("Must create the server before executing operation on it");
        }
        if (this.config.isBootstrap()) {
            // Synchronize in bootstrap mode to not create multiple floating ip in the same time
            synchronized (this) {
                FloatingIP floatingIP = null;
                try {
                    String attachedFloatingIP = getAttributeAsString("public_ip_address");
                    if (StringUtils.isBlank(attachedFloatingIP)) {
                        if (this.externalNetworkId != null) {
                            // In bootstrap mode if the VM does not have any floating ip assigned
                            // We assign to it a floating ip before executing operation
                            floatingIP = floatingIPApi.allocateFromPool(this.externalNetworkId);
                            floatingIPApi.addToServer(floatingIP.getIp(), this.server.getId());
                            destroySshExecutor();
                            initSshExecutor(floatingIP.getIp());
                            log.info("Bootstrap mode : for node {} created new floating ip {} in bootstrap mode in order to access to the machine", getId(), floatingIP.getIp());
                        } else {
                            log.info("Bootstrap mode : for node {} no external network configured, will establish connection with private IP {}", getId(), this.ipAddress);
                            if (sshExecutor == null) {
                                initSshExecutor(this.ipAddress);
                            }
                        }
                    } else {
                        log.info("Bootstrap mode : node {} is connected to external network, will establish connection with public IP {}", getId(), attachedFloatingIP);
                        if (sshExecutor == null) {
                            initSshExecutor(attachedFloatingIP);
                        }
                    }
                    return doExecute(nodeId, operationArtifactPath, inputs);
                } finally {
                    // Remove the floating ip at the end of the operation
                    if (floatingIP != null) {
                        try {
                            floatingIPApi.delete(floatingIP.getId());
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } else {
            return doExecute(nodeId, operationArtifactPath, inputs);
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
        for (Network internalNetwork : networks) {
            internalNetworks.add(internalNetwork.getNetworkId());
        }
        if (StringUtils.isNotEmpty(this.networkId)) {
            internalNetworks.add(this.networkId);
        }
        if (!internalNetworks.isEmpty()) {
            createServerOptions.networks(internalNetworks);
        }
        if (securityGroups != null) {
            createServerOptions.securityGroupNames(securityGroups);
        }
        log.info("Creating server with info:\n- AVZ: {}\n- Config drive: {}\n- Disk config: {}\n- Key pair: {}\n- Networks: {}\n- Security groups: {}\n- User data: {}",
                createServerOptions.getAvailabilityZone(),
                createServerOptions.getConfigDrive(),
                createServerOptions.getDiskConfig(),
                createServerOptions.getKeyPairName(),
                createServerOptions.getNetworks(),
                createServerOptions.getSecurityGroupNames(),
                userData);
        ServerCreated serverCreated = this.serverApi.create(config.getDeploymentName().replaceAll("[^\\p{L}\\p{Nd}]+", "") + "_" + this.getId(), getMandatoryPropertyAsString("image"), getMandatoryPropertyAsString("flavor"), createServerOptions);
        this.server = serverApi.get(serverCreated.getId());
        log.info("Created server with id " + this.server);
    }

    private void destroySshExecutor() {
        if (sshExecutor != null) {
            try {
                sshExecutor.close();
            } catch (IOException e) {
                log.warn("Cannot close SSH Session", e);
            }
            this.sshExecutor = null;
        }
    }

    private void initSshExecutor(String ipForSSSHSession) {
        if (StringUtils.isBlank(ipForSSSHSession)) {
            throw new ToscaRuntimeException("IP of the server " + getId() + "is null, maybe it was not initialized properly or has been deleted");
        }
        String user = getMandatoryPropertyAsString("login");
        String keyPath = getMandatoryPropertyAsString("key_path");
        String absoluteKeyPath = this.config.getTopologyResourcePath().resolve(keyPath).toString();
        String port = getPropertyAsString("ssh_port", "22");
        this.sshExecutor = new SSHExecutor(user, ipForSSSHSession, Integer.parseInt(port), absoluteKeyPath);
        try {
            String operationName = "create ssh session  " + user + "@" + ipForSSSHSession + " with key : " + keyPath;
            RetryUtil.Action<Void> action = () -> {
                sshExecutor.init();
                return null;
            };
            RetryUtil.doActionWithRetry(action, operationName, 240, 5000L, RuntimeSshException.class, SshException.class);
        } catch (Throwable e) {
            log.error("Unable to create ssh session  " + user + "@" + ipForSSSHSession + " with key : " + keyPath, e);
            throw new OperationExecutionException("Unable to create ssh session  " + user + "@" + ipForSSSHSession + " with key : " + keyPath, e);
        }
    }

    @Override
    public void start() {
        super.start();
        if (this.server == null) {
            throw new ToscaRuntimeException("Must create the server before starting it");
        }
        RetryUtil.waitUntilPredicateIsSatisfied(() -> {
            boolean isUp = Server.Status.ACTIVE.equals(server.getStatus())
                    && server.getAddresses() != null
                    && !server.getAddresses().isEmpty();
            if (!isUp) {
                log.info("Compute [{}]: Waiting for server [{}] to be up, current state [{}]", getId(), server.getId(), server.getStatus());
                server = serverApi.get(server.getId());
                return false;
            } else {
                return true;
            }
        }, 2, TimeUnit.SECONDS);
        this.ipAddress = server.getAddresses().values().iterator().next().getAddr();
        for (ExternalNetwork externalNetwork : this.externalNetworks) {
            FloatingIP floatingIP = this.floatingIPApi.allocateFromPool(externalNetwork.getNetworkId());
            this.createdFloatingIPs.add(floatingIP);
            this.floatingIPApi.addToServer(floatingIP.getIp(), this.server.getId());
            setAttribute("public_ip_address", floatingIP.getIp());
            log.info("Attached floating ip " + floatingIP.getIp() + " to compute " + this.getId());
        }
        setAttribute("ip_address", this.ipAddress);
        setAttribute("provider_resource_id", server.getId());
        setAttribute("provider_resource_name", server.getName());
        if (!this.config.isBootstrap()) {
            // If it's not in bootstrap mode initialize immediately ssh session
            initSshExecutor(ipAddress);
        }
        log.info("Started server with info " + server);
    }

    @Override
    public void stop() {
        super.stop();
        if (this.server == null) {
            log.warn("Server has not been started yet");
            return;
        }
        destroySshExecutor();
        this.serverApi.stop(this.server.getId());
        RetryUtil.waitUntilPredicateIsSatisfied(() -> {
            boolean isShutOff = Server.Status.SHUTOFF.equals(server.getStatus());
            if (!isShutOff) {
                log.info("Compute [{}]: Waiting for server [{}] to be shutdown, current state [{}]", getId(), server.getId(), server.getStatus());
                server = serverApi.get(server.getId());
                return false;
            } else {
                return true;
            }
        }, 2, TimeUnit.SECONDS);
        log.info("Stopped server with id " + this.server);
    }

    @Override
    public void delete() {
        super.delete();
        if (this.server == null) {
            log.warn("Server has not been started yet");
            return;
        }
        for (FloatingIP floatingIP : createdFloatingIPs) {
            this.floatingIPApi.delete(floatingIP.getId());
        }
        this.createdFloatingIPs.clear();
        String serverId = this.server.getId();
        this.serverApi.delete(this.server.getId());
        RetryUtil.waitUntilPredicateIsSatisfied(() -> {
            boolean isDeleted = Server.Status.DELETED.equals(server.getStatus());
            if (!isDeleted) {
                log.info("Compute [{}]: Waiting for server [{}] to be deleted, current state [{}]", getId(), server.getId(), server.getStatus());
                server = serverApi.get(server.getId());
                return server == null;
            } else {
                return true;
            }
        }, 2, TimeUnit.SECONDS);
        log.info("Deleted server with id " + serverId);
        this.server = null;
        this.removeAttribute("ip_address");
        this.removeAttribute("provider_resource_id");
        this.removeAttribute("provider_resource_name");
        this.removeAttribute("public_ip_address");
    }
}
