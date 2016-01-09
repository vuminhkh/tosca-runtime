package com.toscaruntime.docker.nodes;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.model.NetworkFilters;
import com.github.dockerjava.api.model.NetworkIPAM;
import com.toscaruntime.exception.PropertyRequiredException;

/**
 * Docker network implementation
 *
 * @author Minh Khang VU
 */
@SuppressWarnings("unchecked")
public class Network extends tosca.nodes.Network {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    private DockerClient dockerClient;

    private String networkId;

    private String networkName;

    private com.github.dockerjava.api.model.Network findNetwork(String id, String name) {
        if (id == null && name == null) {
            return null;
        }
        NetworkFilters filters = new NetworkFilters();
        if (id != null) {
            filters.withIds(id);
        }
        if (name != null) {
            filters.withNames(name);
        }
        List<com.github.dockerjava.api.model.Network> networks = dockerClient.listNetworksCmd().withFilters(new NetworkFilters().withNames(name)).exec();
        return networks.isEmpty() ? null : networks.iterator().next();
    }

    @Override
    public void create() {
        super.create();
        networkId = getPropertyAsString("network_id");
        networkName = getPropertyAsString("network_name");
        com.github.dockerjava.api.model.Network existing = findNetwork(networkId, networkName);
        if (existing == null) {
            if (StringUtils.isEmpty(networkName)) {
                throw new PropertyRequiredException("network_name is required to create new network");
            }
            String cidr = getMandatoryPropertyAsString("cidr");
            NetworkIPAM networkIPAM = new NetworkIPAM();
            NetworkIPAM.Config config = new NetworkIPAM.Config();
            config.setSubnet(cidr);
            config.setGateway(getPropertyAsString("gateway_ip"));
            config.setIpRange(getPropertyAsString("ip_range"));
            networkIPAM.setConfig(new NetworkIPAM.Config[]{config});
            log.info("Network [" + getId() + "] creating new network with name [" + networkName + "]");
            CreateNetworkCmd createNetworkCmd = dockerClient.createNetworkCmd(networkName)
                    .withIPAM(networkIPAM)
                    .withCheckDuplicate(true);
            String driver = getPropertyAsString("driver");
            if (StringUtils.isNotEmpty(driver)) {
                createNetworkCmd.withDriver(driver);
            }
            Map<String, String> options = (Map<String, String>) getProperty("options");
            if (options != null && !options.isEmpty()) {
                createNetworkCmd.withOptions(options);
            }
            networkId = createNetworkCmd.exec().getId();
            log.info("Network [" + getId() + "] created new network [" + networkName + "] with id [" + networkId + "]");
        } else {
            networkName = existing.getName();
            networkId = existing.getId();
            log.info("Network [" + getId() + "] found existing network [" + networkName + "] with id [" + networkId + "]");
        }
        setAttribute("provider_resource_id", networkId);
        setAttribute("provider_resource_name", networkName);
    }

    @Override
    public void delete() {
        super.delete();
        if (networkId == null) {
            log.warn("Network [" + getId() + "] has not been created yet and so cannot be deleted");
        }
        if (StringUtils.isEmpty(getPropertyAsString("network_id"))) {
            // Only delete if not using external resource
            dockerClient.removeNetworkCmd(networkId).exec();
        }
        networkId = null;
        networkName = null;
    }

    public void setDockerClient(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public String getNetworkId() {
        return networkId;
    }

    public String getNetworkName() {
        return networkName;
    }
}
