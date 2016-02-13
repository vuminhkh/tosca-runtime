package com.toscaruntime.openstack.util;

import java.util.Map;

import com.toscaruntime.tosca.ToscaTime;
import com.toscaruntime.util.PropertyUtil;

public class FailSafeConfigUtil {

    public static int getOpenstackOperationRetry(Map<String, Object> properties) {
        return Integer.parseInt(PropertyUtil.getPropertyAsString(properties, "openstack_fail_safe.operation_retry"));
    }

    public static long getOpenstackWaitBetweenOperationRetry(Map<String, Object> properties) {
        String waitBetweenOperationRetry = PropertyUtil.getPropertyAsString(properties, "openstack_fail_safe.wait_between_operation_retry");
        return new ToscaTime(waitBetweenOperationRetry).value().get().longValue();
    }
}
