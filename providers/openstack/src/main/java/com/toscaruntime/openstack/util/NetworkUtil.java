package com.toscaruntime.openstack.util;

import org.jclouds.collect.IterableWithMarker;
import org.jclouds.openstack.neutron.v2.domain.Network;
import org.jclouds.openstack.neutron.v2.features.NetworkApi;

import com.google.common.base.Optional;

public class NetworkUtil {

    public static Network findNetworkByName(NetworkApi networkApi, String networkName, boolean external) {
        for (IterableWithMarker<Network> networkIterator : networkApi.list()) {
            Optional<Network> optNet = networkIterator.firstMatch(input -> input.getName().equals(networkName) && (external == (input.getExternal() != null && input.getExternal())));
            if (optNet.isPresent()) {
                return optNet.get();
            }
        }
        return null;
    }
}
