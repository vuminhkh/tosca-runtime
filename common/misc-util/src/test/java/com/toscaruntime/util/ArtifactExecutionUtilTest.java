package com.toscaruntime.util;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ArtifactExecutionUtilTest {

    @Test
    public void testProcessInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("a-b", "c");
        inputs.put("1&a", "b");
        Map<String, String> processed = ArtifactExecutionUtil.processInputs(inputs, new HashMap<>(), "", "");
        Assert.assertEquals("c", processed.get("a_b"));
        Assert.assertEquals("b", processed.get("_1_a"));
    }
}
