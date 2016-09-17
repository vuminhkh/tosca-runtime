package com.toscaruntime.common.nodes;

import com.toscaruntime.util.PropertyUtil;
import com.toscaruntime.util.ToscaUtil;

public class BlockStorage extends tosca.nodes.BlockStorage {

    protected int getOperationRetry() {
        return Integer.parseInt(PropertyUtil.getMandatoryPropertyAsString(getProperties(), "provider_fail_safe.operation_retry"));
    }

    protected long getWaitBetweenOperationRetry() {
        String waitBetweenOperationRetry = PropertyUtil.getMandatoryPropertyAsString(getProperties(), "provider_fail_safe.wait_between_operation_retry");
        return ToscaUtil.convertToSeconds(waitBetweenOperationRetry);
    }
}
