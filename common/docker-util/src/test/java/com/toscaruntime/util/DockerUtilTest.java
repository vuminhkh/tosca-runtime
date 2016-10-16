package com.toscaruntime.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.MalformedURLException;

@RunWith(JUnit4.class)
public class DockerUtilTest {
    @Test
    public void testResolveDockerHost() throws MalformedURLException {
        String url = "tcp://192.168.99.100:2376";
        String dockerHost = DockerUtil.getDockerHostName(url);
        Assert.assertEquals("192.168.99.100", dockerHost);
    }
}
