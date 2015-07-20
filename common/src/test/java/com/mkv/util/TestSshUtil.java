package com.mkv.util;

import java.io.IOException;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.Maps;

/**
 * @author Minh Khang VU
 */
public class TestSshUtil {

    @Test
    public void test() throws IOException, InterruptedException {
        Map<String, String> env = Maps.newHashMap();
        env.put("JAVA_HOME", "/opt/java");
        env.put("JAVA_URL", "http://download.oracle.com/otn-pub/java/jdk/7u75-b13/jdk-7u75-linux-x64.tar.gz");
        SSHUtil.executeScript("ubuntu", "129.185.67.36", 22, "/Users/mkv/tosca-docker/src/test/resources/keyOS.pem",
                "/Users/mkv/tosca-docker/src/test/resources/java_install.sh", env);
        // SSHUtil.executeCommand("ubuntu", "129.185.67.36", 22, "/Users/mkv/tosca-docker/src/test/resources/keyOS.pem",
        // "java -version", env);
        // SSHUtil.upload("ubuntu", "129.185.67.36", 22, "/Users/mkv/tosca-docker/src/test/resources/keyOS.pem", "/tmp/test.sh",
        // "/Users/mkv/tosca-docker/src/test/resources/java_install.sh");
        // SSHUtil.download("ubuntu", "129.185.67.36", 22, "/Users/mkv/tosca-docker/src/test/resources/keyOS.pem", "/tmp/test.sh",
        // "/tmp/test.sh");
    }
}
