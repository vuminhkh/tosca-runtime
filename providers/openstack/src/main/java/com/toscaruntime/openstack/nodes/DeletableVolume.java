package com.toscaruntime.openstack.nodes;

import com.toscaruntime.util.SynchronizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class DeletableVolume extends Volume {

    private static final Logger log = LoggerFactory.getLogger(DeletableVolume.class);

    @Override
    public void delete() {
        super.delete();
        if (volume == null) {
            log.warn("Volume [{}] : Volume has not been created yet", getId());
        }
        connection.getVolumeApi().delete(volume.getId());
        String idToDelete = volume.getId();
        String nameToDelete = volume.getName();
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            volume = connection.getVolumeApi().get(idToDelete);
            boolean isDeleted = volume == null;
            if (!isDeleted) {
                log.info("Volume [{}] : Waiting for volume [{}] to be deleted, current state [{}]", getId(), idToDelete, volume.getStatus());
            }
            return isDeleted;
        }, getOperationRetry(), getWaitBetweenOperationRetry(), TimeUnit.SECONDS);
        log.info("Volume [{}] : Deleted volume [{}] and id [{}]", nameToDelete, idToDelete);
        removeAttribute("provider_resource_id");
        removeAttribute("provider_resource_name");
    }
}
