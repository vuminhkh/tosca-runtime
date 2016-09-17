package com.toscaruntime.common.nodes;

import com.toscaruntime.util.PropertyUtil;
import com.toscaruntime.util.ToscaUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Compute extends tosca.nodes.Compute {

    private static final Logger log = LoggerFactory.getLogger(tosca.nodes.Compute.class);

    protected int getOperationRetry() {
        return Integer.parseInt(PropertyUtil.getMandatoryPropertyAsString(getProperties(), "provider_fail_safe.operation_retry"));
    }

    protected long getWaitBetweenOperationRetry() {
        String waitBetweenOperationRetry = PropertyUtil.getMandatoryPropertyAsString(getProperties(), "provider_fail_safe.wait_between_operation_retry");
        return ToscaUtil.convertToSeconds(waitBetweenOperationRetry);
    }

    /**
     * Get ip address on which the micro manager can talk to the compute
     *
     * @return the ip address of the compute
     */
    public String getIpAddress() {
        String privateIPAddress = getAttributeAsString("ip_address");
        if (!this.config.isBootstrap()) {
            return privateIPAddress;
        } else {
            String attachedFloatingIP = getAttributeAsString("public_ip_address");
            if (StringUtils.isBlank(attachedFloatingIP)) {
                log.warn("Compute [{}] : Bootstrap mode is enabled but no public_ip_address can be found on the compute, will use private ip [{}]", getId(), privateIPAddress);
                return privateIPAddress;
            } else {
                log.info("Compute [{}] : Bootstrap mode is enabled, use public ip [{}]", getId(), attachedFloatingIP);
                return attachedFloatingIP;
            }
        }
    }
}
