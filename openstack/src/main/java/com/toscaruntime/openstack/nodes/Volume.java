package com.toscaruntime.openstack.nodes;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.jclouds.openstack.cinder.v1.features.VolumeApi;
import org.jclouds.openstack.cinder.v1.options.CreateVolumeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.exception.deployment.execution.ProviderResourceAllocationException;
import com.toscaruntime.exception.deployment.execution.ProviderResourcesNotFoundException;
import com.toscaruntime.openstack.util.FailSafeConfigUtil;
import com.toscaruntime.tosca.ToscaSize;
import com.toscaruntime.util.FailSafeUtil;
import com.toscaruntime.util.SynchronizationUtil;

import tosca.nodes.BlockStorage;

@SuppressWarnings("unchecked")
public class Volume extends BlockStorage {

    private static final Logger log = LoggerFactory.getLogger(Volume.class);

    protected VolumeApi volumeApi;

    protected org.jclouds.openstack.cinder.v1.domain.Volume volume;

    @Override
    public void initialLoad() {
        super.initialLoad();
        String volumeId = getAttributeAsString("provider_resource_id");
        volume = volumeApi.get(volumeId);
    }

    @Override
    public void create() {
        super.create();
        String volumeId = getPropertyAsString("volume_id");
        if (StringUtils.isBlank(volumeId)) {
            int sizeInGIB = new ToscaSize(getMandatoryPropertyAsString("size")).convertToUnit("GIB").base().get().toInt();
            String availabilityZone = getPropertyAsString("availability_zone");
            String description = getPropertyAsString("volume_description");
            String volumeType = getPropertyAsString("volume_type");
            String snapshotId = getPropertyAsString("snapshot_id");
            Map<String, String> metadata = (Map<String, String>) getProperty("metadata");
            CreateVolumeOptions options = new CreateVolumeOptions();
            options.name(getId());
            if (StringUtils.isNotBlank(availabilityZone)) {
                options.availabilityZone(availabilityZone);
            }
            if (StringUtils.isNotBlank(description)) {
                options.description(description);
            }
            if (StringUtils.isNotBlank(availabilityZone)) {
                options.snapshotId(snapshotId);
            }
            if (StringUtils.isNotBlank(availabilityZone)) {
                options.volumeType(volumeType);
            }
            if (metadata != null) {
                options.metadata(metadata);
            }
            log.info("Volume [{}] : Create new volume with size [{}] GIB", getId(), sizeInGIB);
            try {
                FailSafeUtil.doActionWithRetry(
                        () -> volume = volumeApi.create(sizeInGIB, options),
                        "Create volume",
                        getOpenstackOperationRetry(),
                        getOpenstackWaitBetweenOperationRetry(),
                        TimeUnit.SECONDS,
                        Exception.class
                );
            } catch (Throwable throwable) {
                throw new ProviderResourceAllocationException("Volume [" + getId() + "] : Could not create volume", throwable);
            }
        } else {
            log.info("Volume [{}] : Reusing existing volume [{}]", volumeId);
            volume = volumeApi.get(volumeId);
            if (volume == null) {
                throw new ProviderResourcesNotFoundException("Volume [" + getId() + "] : Volume with id [" + volumeId + "] cannot be found");
            }
        }
        waitForStatus(org.jclouds.openstack.cinder.v1.domain.Volume.Status.AVAILABLE);
        setAttribute("provider_resource_id", volume.getId());
        setAttribute("provider_resource_name", volume.getName());
    }

    public void waitForStatus(org.jclouds.openstack.cinder.v1.domain.Volume.Status status) {
        int retryNumber = getOpenstackOperationRetry();
        long coolDownPeriod = getOpenstackWaitBetweenOperationRetry();
        boolean statusReached = SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            boolean isAvailable = status.equals(volume.getStatus());
            if (!isAvailable) {
                log.info("Volume [{}] : Volume [{}] waiting to become [{}], current state [{}]", getId(), volume.getId(), status, volume.getStatus());
                volume = volumeApi.get(volume.getId());
                return false;
            } else {
                return true;
            }
        }, retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        if (!statusReached) {
            throw new ProviderResourceAllocationException("Volume [" + getId() + "] : Could not wait for volume to reach status [" + status + "] after [" + retryNumber + "] retries with cool down period of [" + coolDownPeriod + "] seconds");
        }
    }

    public int getOpenstackOperationRetry() {
        return FailSafeConfigUtil.getOpenstackOperationRetry(getProperties());
    }

    public long getOpenstackWaitBetweenOperationRetry() {
        return FailSafeConfigUtil.getOpenstackWaitBetweenOperationRetry(getProperties());
    }

    public void setVolumeApi(VolumeApi volumeApi) {
        this.volumeApi = volumeApi;
    }

    public org.jclouds.openstack.cinder.v1.domain.Volume getVolume() {
        return volume;
    }
}
