package com.toscaruntime.docker.util;

import java.util.HashMap;
import java.util.Map;

public class IpAddressUtil {

    public static Map<String, String> extractSwarmNodesIpsMappings(Map<String, Object> bootstrapContext) {
        Map<String, String> ipMappings = extractSwarmNodesIpsMappings(bootstrapContext, "manager_ip_addresses", "manager_public_ip_addresses");
        ipMappings.putAll(extractSwarmNodesIpsMappings(bootstrapContext, "node_ip_addresses", "node_public_ip_addresses"));
        return ipMappings;
    }

    private static Map<String, String> extractSwarmNodesIpsMappings(Map<String, Object> bootstrapContext, String ipKey, String publicIpKey) {
        Object ipValue = bootstrapContext.get(ipKey);
        Object publicIpValue = bootstrapContext.get(publicIpKey);
        if (ipValue == null) {
            return new HashMap<>();
        }
        Map<String, String> ipMappings = new HashMap<>();
        if (ipValue instanceof Map) {
            Map<String, String> ipMap = (Map<String, String>) ipValue;
            for (Map.Entry<String, String> ipMapEntry : ipMap.entrySet()) {
                String nodeId = ipMapEntry.getKey();
                String ip = ipMapEntry.getValue();
                String publicIp;
                if (publicIpValue instanceof Map) {
                    publicIp = ((Map<String, String>) publicIpValue).get(nodeId);
                } else {
                    publicIp = (String) publicIpValue;
                }
                ipMappings.put(ip, publicIp);
            }
        } else {
            ipMappings.put((String) ipValue, (String) publicIpValue);
        }
        return ipMappings;
    }
}
