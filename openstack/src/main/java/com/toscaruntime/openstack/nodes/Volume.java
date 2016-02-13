package com.toscaruntime.openstack.nodes;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.jclouds.openstack.cinder.v1.features.VolumeApi;
import org.jclouds.openstack.cinder.v1.options.CreateVolumeOptions;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeAttachmentApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.exception.OperationExecutionException;
import com.toscaruntime.exception.ProviderResourceAllocationException;
import com.toscaruntime.exception.ProviderResourcesNotFoundException;
import com.toscaruntime.openstack.util.FailSafeConfigUtil;
import com.toscaruntime.tosca.ToscaSize;
import com.toscaruntime.util.FailSafeUtil;
import com.toscaruntime.util.SynchronizationUtil;

import tosca.nodes.BlockStorage;

@SuppressWarnings("unchecked")
public class Volume extends BlockStorage {

    private static final Logger log = LoggerFactory.getLogger(Volume.class);

    protected VolumeApi volumeApi;

    protected VolumeAttachmentApi volumeAttachmentApi;

    protected Compute owner;

    protected org.jclouds.openstack.cinder.v1.domain.Volume volume;

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
            log.info("Volume [{}] : Create new volume for [{}] with size in GIB [{}]", getId(), sizeInGIB);
            volume = volumeApi.create(sizeInGIB, options);
        } else {
            log.info("Volume [{}] : Reusing existing volume [{}]", volumeId);
            volume = volumeApi.get(volumeId);
            if (volume == null) {
                throw new ProviderResourcesNotFoundException("Volume [" + getId() + "] : Volume with id [" + volumeId + "] cannot be found");
            }
        }
    }

    private void waitForStatus(org.jclouds.openstack.cinder.v1.domain.Volume.Status status) {
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

    @Override
    public void start() {
        super.start();
        if (volume == null) {
            throw new OperationExecutionException("Volume [" + getId() + "] : Must create volume before starting it");
        }
        waitForStatus(org.jclouds.openstack.cinder.v1.domain.Volume.Status.AVAILABLE);
        if (owner != null) {
            volumeAttachmentApi.attachVolumeToServerAsDevice(volume.getId(), owner.getAttributeAsString("provider_resource_id"), getPropertyAsString("device"));
            waitForStatus(org.jclouds.openstack.cinder.v1.domain.Volume.Status.IN_USE);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (volume == null) {
            log.warn("Volume [{}] : Volume has not been created yet", getId());
            return;
        }
        if (owner != null) {
            String serverId = owner.getAttributeAsString("provider_resource_id");
            try {
                FailSafeUtil.doActionWithRetry(
                        () -> volumeAttachmentApi.detachVolumeFromServer(volume.getId(), serverId),
                        "Attach floating ip",
                        getOpenstackOperationRetry(),
                        getOpenstackWaitBetweenOperationRetry(),
                        TimeUnit.SECONDS,
                        Exception.class);
            } catch (Throwable throwable) {
                throw new ProviderResourceAllocationException("Volume [" + getId() + "] : Could not attach volume " + volume.getId() + " to server " + serverId);
            }
            volumeAttachmentApi.detachVolumeFromServer(volume.getId(), owner.getAttributeAsString("provider_resource_id"));
            waitForStatus(org.jclouds.openstack.cinder.v1.domain.Volume.Status.AVAILABLE);
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

    public void setVolumeAttachmentApi(VolumeAttachmentApi volumeAttachmentApi) {
        this.volumeAttachmentApi = volumeAttachmentApi;
    }

    public void setOwner(Compute owner) {
        this.owner = owner;
    }
}
