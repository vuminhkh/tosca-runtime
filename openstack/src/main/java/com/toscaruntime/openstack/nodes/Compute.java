package com.toscaruntime.openstack.nodes;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

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
import com.toscaruntime.exception.NonRecoverableException;
import com.toscaruntime.util.RetryUtil;
import com.toscaruntime.util.SSHExecutor;

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

    private String serverId;

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

    public String getServerId() {
        return serverId;
    }

    private void doExecute(String operationArtifactPath, Map<String, String> inputs) {
        try {
            RetryUtil.doActionWithRetry(new RetryUtil.Action<Object>() {
                @Override
                public String getName() {
                    return operationArtifactPath;
                }

                @Override
                public Object doAction() throws Throwable {
                    sshExecutor.executeScript(config.getArtifactsPath().resolve(operationArtifactPath).toString(), inputs);
                    return null;
                }
            }, 12, 30000L, RuntimeSshException.class, SshException.class);
        } catch (Throwable e) {
            throw new NonRecoverableException("Unable to execute operation " + operationArtifactPath, e);
        }
    }

    @Override
    public void execute(String operationArtifactPath, Map<String, String> inputs) {
        if (this.config.isBootstrap()) {
            // Synchronize in bootstrap mode to not create multiple floating ip in the same time
            synchronized (this) {
                FloatingIP floatingIP = null;
                try {
                    String attachedFloatingIP = getAttribute("public_ip_address");
                    if (StringUtils.isBlank(attachedFloatingIP)) {
                        if (this.externalNetworkId != null) {
                            // In bootstrap mode if the VM does not have any floating ip assigned
                            // We assign to it a floating ip before executing operation
                            floatingIP = floatingIPApi.allocateFromPool(this.externalNetworkId);
                            floatingIPApi.addToServer(floatingIP.getIp(), this.serverId);
                            destroySshExecutor();
                            initSshExecutor(floatingIP.getIp());
                            log.info("Created new floating ip {} in bootstrap mode in order to access to the machine", floatingIP.getIp());
                        } else {
                            if (sshExecutor == null) {
                                initSshExecutor(this.ipAddress);
                            }
                        }
                    } else {
                        if (sshExecutor == null) {
                            initSshExecutor(attachedFloatingIP);
                        }
                    }
                    doExecute(operationArtifactPath, inputs);
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
            doExecute(operationArtifactPath, inputs);
        }
    }

    @Override
    public void create() {
        String userData = getProperty("user_data");
        String networksProperty = getProperty("networks");
        String securityGroups = getProperty("security_group_names");
        String adminPass = getProperty("admin_pass");
        String availabilityZone = getProperty("availability_zone");
        String configDrive = getProperty("config_drive");
        String diskConfig = getProperty("disk_config");
        String keyPairName = getProperty("key_pair_name");
        CreateServerOptions createServerOptions = new CreateServerOptions();
        if (StringUtils.isNotBlank(adminPass)) {
            createServerOptions.adminPass(adminPass);
        }
        if (StringUtils.isNotBlank(availabilityZone)) {
            createServerOptions.availabilityZone(availabilityZone);
        }
        if (StringUtils.isNotBlank(configDrive)) {
            createServerOptions.availabilityZone(configDrive);
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
        if (StringUtils.isNotBlank(networksProperty)) {
            internalNetworks.addAll(Arrays.asList(networksProperty.trim().split("\\s*,\\s*")));
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
        if (StringUtils.isNotBlank(securityGroups)) {
            createServerOptions.securityGroupNames(securityGroups.trim().split("\\s*,\\s*"));
        }
        ServerCreated serverCreated = this.serverApi.create(this.getId(), getMandatoryProperty("image"), getMandatoryProperty("flavor"), createServerOptions);
        this.serverId = serverCreated.getId();
        log.info("Created server with id " + this.serverId);
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
        String user = getMandatoryProperty("login");
        String keyPath = getMandatoryProperty("key_path");
        String absoluteKeyPath = this.config.getTopologyResourcePath().resolve(keyPath).toString();
        String port = getProperty("ssh_port", "22");
        this.sshExecutor = new SSHExecutor(user, ipForSSSHSession, Integer.parseInt(port), absoluteKeyPath);
        try {
            RetryUtil.doActionWithRetry(new RetryUtil.Action<Object>() {
                @Override
                public String getName() {
                    return "create ssh session  " + user + "@" + ipForSSSHSession + " with key : " + keyPath;
                }

                @Override
                public Object doAction() throws Throwable {
                    sshExecutor.init();
                    return null;
                }
            }, 240, 5000L, RuntimeSshException.class, SshException.class);
        } catch (Throwable e) {
            log.error("Unable to create ssh session  " + user + "@" + ipForSSSHSession + " with key : " + keyPath, e);
            throw new NonRecoverableException("Unable to create ssh session  " + user + "@" + ipForSSSHSession + " with key : " + keyPath, e);
        }
    }

    @Override
    public void start() {
        super.start();
        if (this.serverId == null) {
            throw new NonRecoverableException("Must create the server before starting it");
        }
        Server server;
        while (!Server.Status.ACTIVE.equals((server = serverApi.get(this.serverId)).getStatus())) {
            log.info("Waiting for compute " + getId() + " with openstack id " + getServerId() + " to become active");
            try {
                Thread.sleep(2 * 1000L);
            } catch (InterruptedException e) {
                throw new NonRecoverableException("Start interrupted");
            }
        }
        while (server.getAddresses() == null || server.getAddresses().isEmpty()) {
            log.info("Waiting for compute " + getId() + " with openstack id " + getServerId() + " has a private IP assigned");
            try {
                Thread.sleep(2 * 1000L);
            } catch (InterruptedException e) {
                throw new NonRecoverableException("Start interrupted");
            }
            server = serverApi.get(this.serverId);
        }
        this.ipAddress = server.getAddresses().values().iterator().next().getAddr();
        for (ExternalNetwork externalNetwork : this.externalNetworks) {
            FloatingIP floatingIP = this.floatingIPApi.allocateFromPool(externalNetwork.getNetworkId());
            if (floatingIP == null) {
                throw new NonRecoverableException("Could not allocate floating ip from pool " + externalNetwork.getNetworkId());
            }
            this.floatingIPApi.addToServer(floatingIP.getIp(), this.serverId);
            this.getAttributes().put("public_ip_address", floatingIP.getIp());
            this.createdFloatingIPs.add(floatingIP);
            log.info("Attached floating ip " + floatingIP.getIp() + " to compute " + this.getId());
        }
        getAttributes().put("ip_address", this.ipAddress);
        getAttributes().put("tosca_id", server.getId());
        getAttributes().put("tosca_name", server.getName());
        if (!this.config.isBootstrap()) {
            // If it's not in bootstrap mode initialize immediately ssh session
            initSshExecutor(ipAddress);
        }
        log.info("Started server with info " + server);
    }

    @Override
    public void stop() {
        super.stop();
        if (this.serverId == null) {
            log.warn("Server has not been started yet");
            return;
        }
        destroySshExecutor();
        this.serverApi.stop(this.serverId);
        log.info("Stopped server with id " + this.serverId);
    }

    @Override
    public void delete() {
        super.delete();
        if (this.serverId == null) {
            log.warn("Server has not been started yet");
            return;
        }
        for (FloatingIP floatingIP : createdFloatingIPs) {
            this.floatingIPApi.delete(floatingIP.getId());
        }
        this.createdFloatingIPs.clear();
        this.serverApi.delete(this.serverId);
        log.info("Deleted server with id " + this.serverId);
    }
}
