package com.toscaruntime.docker.nodes;

import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.CreateVolumeResponse;
import com.github.dockerjava.api.command.InspectVolumeResponse;

@SuppressWarnings("unchecked")
public class Volume extends tosca.nodes.BlockStorage {

    private static final Logger log = LoggerFactory.getLogger(Volume.class);

    protected DockerClient dockerClient;

    protected String volumeId;

    protected String mountPoint;

    protected String driver;

    protected String location;

    protected Container container;

    @Override
    public void initialLoad() {
        super.initialLoad();
        volumeId = getAttributeAsString("provider_resource_id");
        mountPoint = getAttributeAsString("mount_point");
        driver = getAttributeAsString("driver");
        location = getMandatoryPropertyAsString("location");
    }

    @Override
    public void create() {
        super.create();
        location = getMandatoryPropertyAsString("location");
        volumeId = getPropertyAsString("volume_id");
        if (StringUtils.isNotBlank(volumeId)) {
            try {
                InspectVolumeResponse volumeResponse = dockerClient.inspectVolumeCmd(volumeId).exec();
                mountPoint = volumeResponse.getMountpoint();
                driver = volumeResponse.getDriver();
                initializeAttributes();
                log.info("Volume [{}] : Found existing volume {} mounted at {} with driver {}", getId(), volumeId, mountPoint, driver);
            } catch (Exception e) {
                // Means volume not found
                doCreate(volumeId);
            }
        } else {
            volumeId = UUID.randomUUID().toString();
            doCreate(volumeId);
        }
    }

    @Override
    public void start() {
        super.start();
        container.attachVolume(this);
    }

    private void doCreate(String name) {
        CreateVolumeCmd createVolumeCmd = dockerClient.createVolumeCmd();
        createVolumeCmd.withName(name);
        driver = getPropertyAsString("volume_driver");
        Map<String, String> driverOpts = (Map<String, String>) getProperty("volume_driver_opts");
        if (StringUtils.isNotBlank(driver)) {
            createVolumeCmd.withDriver(driver);
        }
        if (driverOpts != null && !driverOpts.isEmpty()) {
            createVolumeCmd.withDriverOpts(driverOpts);
        }
        CreateVolumeResponse volumeResponse = createVolumeCmd.exec();
        volumeId = volumeResponse.getName();
        mountPoint = volumeResponse.getMountpoint();
        driver = volumeResponse.getDriver();
        log.info("Volume [{}]: Created new volume {} mounted at {} with driver {}", getId(), volumeId, mountPoint, driver);
        initializeAttributes();
    }

    private void initializeAttributes() {
        setAttribute("provider_resource_id", volumeId);
        setAttribute("provider_resource_name", volumeId);
        setAttribute("driver", driver);
        setAttribute("mount_point", mountPoint);
    }

    public String getVolumeId() {
        return volumeId;
    }

    /**
     * Get the mount point. Take note that the mount point is the path on the docker host that the volume is bound to.
     *
     * @return the volume's mount point on docker host.
     */
    public String getMountPoint() {
        return mountPoint;
    }

    public String getDriver() {
        return driver;
    }

    /**
     * The location is the path inside the docker container that point to the mounted volume
     *
     * @return The volume location inside the docker container
     */
    public String getLocation() {
        return location;
    }

    public void setDockerClient(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public void setContainer(Container container) {
        this.container = container;
    }
}
