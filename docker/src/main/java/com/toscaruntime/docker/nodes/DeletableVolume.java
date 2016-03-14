package com.toscaruntime.docker.nodes;

/**
 * Volume that can be deleted
 *
 * @author Minh Khang VU
 */
public class DeletableVolume extends Volume {

    @Override
    public void delete() {
        super.delete();
        dockerClient.removeVolumeCmd(volumeId).exec();
    }
}
