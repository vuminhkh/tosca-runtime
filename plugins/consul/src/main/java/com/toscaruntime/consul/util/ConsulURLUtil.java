package com.toscaruntime.consul.util;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConsulURLUtil {

    public static List<String> extractConsulURLs(Map<String, Object> bootstrapContext) {
        List<String> consulAddresses = extractConsulURLs(bootstrapContext, "consul_server_addresses");
        consulAddresses.addAll(extractConsulURLs(bootstrapContext, "consul_client_addresses"));
        return consulAddresses;
    }


    private static List<String> extractConsulURLs(Map<String, Object> bootstrapContext, String key) {
        Object urlValue = bootstrapContext.get(key);
        if (urlValue == null) {
            return new ArrayList<>();
        } else if (urlValue instanceof Map) {
            Map<String, String> urlMap = (Map<String, String>) urlValue;
            return new ArrayList<>(urlMap.values());
        } else {
            return Lists.newArrayList((String) urlValue);
        }
    }
}