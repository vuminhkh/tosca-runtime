package com.toscaruntime.openstack.nodes;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.util.SynchronizationUtil;

public class DeletableVolume extends Volume {

    private static final Logger log = LoggerFactory.getLogger(DeletableVolume.class);

    @Override
    public void delete() {
        super.delete();
        if (volume == null) {
            log.warn("Volume [{}] : Volume has not been created yet", getId());
        }
        volumeApi.delete(volume.getId());
        String idToDelete = volume.getId();
        String nameToDelete = volume.getName();
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            volume = volumeApi.get(idToDelete);
            boolean isDeleted = volume == null;
            if (!isDeleted) {
                log.info("Volume [{}] : Waiting for volume [{}] to be deleted, current state [{}]", getId(), idToDelete, volume.getStatus());
            }
            return isDeleted;
        }, getOpenstackOperationRetry(), getOpenstackWaitBetweenOperationRetry(), TimeUnit.SECONDS);
        log.info("Volume [{}] : Deleted volume [{}] and id [{}]", nameToDelete, idToDelete);
    }
}
