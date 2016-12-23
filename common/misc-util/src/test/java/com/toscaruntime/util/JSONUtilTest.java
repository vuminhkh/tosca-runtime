package com.toscaruntime.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class JSONUtilTest {

    @Test
    public void testMap() throws IOException {
        Map<String, Object> read = JSONUtil.toMap("{\"wait_between_connect_retry\" : \"5 s\", \"connect_retry\" : \"600\", \"artifact_execution_retry\" : \"6\", \"wait_before_artifact_execution\" : \"10 s\", \"wait_between_artifact_execution_retry\" : \"10 s\"}");
        Assert.assertEquals("5 s", read.get("wait_between_connect_retry"));
        Assert.assertEquals("600", read.get("connect_retry"));
        Assert.assertEquals("10 s", read.get("wait_between_artifact_execution_retry"));
    }

    @Test
    public void testList() throws IOException {
        List<Object> read = JSONUtil.toList("[{\"first\":\"firstValue\"}, {\"last\": {\"lastFirstKey\": \"lastFirstValue\"}}]");
        Assert.assertEquals(2, read.size());
        Assert.assertEquals("firstValue", PropertyUtil.getPropertyAsString((Map<String, Object>) read.get(0), "first"));
        Assert.assertEquals("lastFirstValue", PropertyUtil.getPropertyAsString((Map<String, Object>) read.get(1), "last.lastFirstKey"));
    }
}
