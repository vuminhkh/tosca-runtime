package com.toscaruntime.openstack.nodes;

import com.toscaruntime.exception.OperationExecutionException;

public class DeletableVolume extends Volume {

    @Override
    public void delete() {
        super.delete();
        if (volume == null) {
            throw new OperationExecutionException("Volume has not been created yet");
        }
        volumeApi.delete(volume.getId());
    }
}
