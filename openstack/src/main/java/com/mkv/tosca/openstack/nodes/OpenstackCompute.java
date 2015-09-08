package com.mkv.tosca.openstack.nodes;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tosca.nodes.Compute;

import com.google.common.base.Charsets;
import com.mkv.exception.NonRecoverableException;
import com.mkv.util.SSHUtil;

public class OpenstackCompute extends Compute {

    private static final Logger log = LoggerFactory.getLogger(OpenstackCompute.class);

    private NovaApi novaApi;

    private ServerApi serverApi;

    private String serverId;

    private Server server;

    public void setNovaApi(NovaApi novaApi) {
        this.novaApi = novaApi;
        this.serverApi = this.novaApi.getServerApi(getProperty("region"));
    }

    @Override
    public void execute(String operationArtifactPath, Map<String, String> inputs) {
        String user = getMandatoryProperty("login");
        String keyPath = getMandatoryProperty("key_path");
        String absoluteKeyPath = Paths.get(this.recipeLocalPath, keyPath).toString();
        String port = getProperty("ssh_port", "22");
        try {
            SSHUtil.executeScript(user, this.server.getAccessIPv4(), Integer.parseInt(port), absoluteKeyPath, operationArtifactPath, inputs);
        } catch (IOException | InterruptedException e) {
            throw new NonRecoverableException("Unable to execute operation " + operationArtifactPath, e);
        }
    }

    @Override
    public void create() {
        String userData = getProperty("user_data");
        String networks = getProperty("networks");
        String securityGroups = getProperty("security_group_names");
        CreateServerOptions createServerOptions = CreateServerOptions.Builder
                .adminPass(getProperty("admin_pass"))
                .availabilityZone(getProperty("availability_zone"))
                // .blockDeviceMappings(null)
                .configDrive(Boolean.parseBoolean(getProperty("config_drive")))
                .diskConfig(getProperty("disk_config"))
                .keyPairName(getProperty("key_pair_name"))
                // .metadata(null)
                .networks(networks != null ? networks.split(",") : new String[0])
                .securityGroupNames(securityGroups != null ? securityGroups.split(",") : new String[0])
                .userData(userData != null ? userData.getBytes(Charsets.UTF_8) : null);
        ServerCreated serverCreated = this.serverApi.create(this.getId(), getProperty("image"), getProperty("flavor"),
                createServerOptions);
        this.serverId = serverCreated.getId();
        log.info("Created server with id " + this.serverId);
    }

    @Override
    public void configure() {
    }

    @Override
    public void start() {
        if (this.serverId == null) {
            throw new NonRecoverableException("Must create the server before starting it");
        }
        this.serverApi.start(this.serverId);
        this.server = serverApi.get(this.serverId);
        getAttributes().put("ip_address", this.server.getAccessIPv4());
        getAttributes().put("tosca_id", this.server.getUuid());
        getAttributes().put("tosca_name", this.server.getName());
        log.info("Started server with info " + this.server);
    }

    @Override
    public void stop() {
        if (this.serverId == null) {
            log.warn("Server has not been started yet");
        }
        this.serverApi.stop(this.serverId);
        log.info("Stopped server with id " + this.serverId);
    }

    @Override
    public void delete() {
        if (this.serverId == null) {
            log.warn("Server has not been started yet");
        }
        this.serverApi.delete(this.serverId);
        log.info("Deleted server with id " + this.serverId);
    }
}
