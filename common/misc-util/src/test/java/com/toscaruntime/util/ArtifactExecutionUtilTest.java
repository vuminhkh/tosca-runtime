package com.toscaruntime.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class ArtifactExecutionUtilTest {

    @Test
    public void testProcessInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("a-b", "c");
        inputs.put("1&a", "b");
        Map<String, Object> processed = ArtifactExecutionUtil.processInputs(inputs);
        Assert.assertEquals("c", processed.get("a_b"));
        Assert.assertEquals("b", processed.get("_1_a"));
    }
}
