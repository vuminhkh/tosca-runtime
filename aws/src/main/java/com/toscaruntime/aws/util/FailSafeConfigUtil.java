package com.toscaruntime.aws.util;

import com.toscaruntime.util.PropertyUtil;
import com.toscaruntime.util.ToscaUtil;

import java.util.Map;

public class FailSafeConfigUtil {

    public static int getAWSOperationRetry(Map<String, Object> properties) {
        return Integer.parseInt(PropertyUtil.getMandatoryPropertyAsString(properties, "aws_fail_safe.operation_retry"));
    }

    public static long getAWSkWaitBetweenOperationRetry(Map<String, Object> properties) {
        String waitBetweenOperationRetry = PropertyUtil.getMandatoryPropertyAsString(properties, "aws_fail_safe.wait_between_operation_retry");
        return ToscaUtil.convertToSeconds(waitBetweenOperationRetry);
    }
}
